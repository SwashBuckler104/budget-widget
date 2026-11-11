package io.github.swashbuckler104.budgetwidget.core.data.backup

import android.database.SQLException
import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.CategorySeeder
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestPipeline
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/** What [DataTransfer.restore] did. */
sealed interface RestoreResult {
    data class Restored(val transactions: Int, val categories: Int) : RestoreResult

    /** Not a backup from this app, or damaged. Nothing was changed. */
    data object NotABackup : RestoreResult

    /** Written by a newer version of the app. Nothing was changed. */
    data class TooNew(val version: Int) : RestoreResult
}

/**
 * Moving data in and out (SCOPE.md F9, ADR-0027): JSON backup and restore, CSV export, and
 * "start fresh". Everything goes through streams the caller opened (Storage Access Framework);
 * there is no network. Writes take one database transaction, so a failure changes nothing.
 */
@Singleton
class DataTransfer @Inject constructor(
    private val db: BudgetDatabase,
    private val settings: SettingsStore,
    private val checkpoint: IngestCheckpointStore,
    private val seeder: CategorySeeder,
    private val events: InboxEvents,
    private val clock: WallClock,
    private val pipeline: IngestPipeline,
    private val sync: SmsSync,
) {
    private val mutex = Mutex()

    /** Writes a full backup to [output] (not closed). [appVersion] is recorded for support. */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun backup(output: OutputStream, appVersion: String) = mutex.withLock {
        val file = db.withTransaction {
            val dao = db.backupDao()
            BackupFile(
                exportedAt = clock.nowMillis(),
                appVersion = appVersion,
                seedVersion = settings.seededCategoriesVersion(),
                categories = dao.categories().map { it.toBackup() },
                accounts = dao.accounts().map { it.toBackup() },
                transactions = dao.transactions().map { it.toBackup() },
                merchantRules = dao.rules().map { it.toBackup() },
                merchantAliases = dao.aliases().map { it.toBackup() },
                dayCloses = dao.dayCloses().map { it.toBackup() },
                settings = settings.snapshot().toBackup(),
            )
        }
        withContext(Dispatchers.IO) { JSON.encodeToStream(file, output) }
    }

    /**
     * Replaces categories, accounts, transactions, rules, aliases, closed days and settings with
     * [input]'s. The unparsed SMS list is local diagnostics and is cleared, not restored.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restore(input: InputStream): RestoreResult = mutex.withLock {
        val file = try {
            withContext(Dispatchers.IO) { JSON.decodeFromStream<BackupFile>(input) }
        } catch (_: SerializationException) {
            // Not logged: the message may quote the file's contents (PRIVACY.md).
            return@withLock RestoreResult.NotABackup
        } catch (_: IllegalArgumentException) {
            return@withLock RestoreResult.NotABackup
        }
        if (file.format != BackupFile.FORMAT) return@withLock RestoreResult.NotABackup
        if (file.version > BackupFile.VERSION) return@withLock RestoreResult.TooNew(file.version)
        try {
            // Ingestion is paused meanwhile, and scans already running are made stale.
            pipeline.replacingData { db.withTransaction { replaceAll(file) } }
        } catch (_: SQLException) {
            // Broken references (a transaction pointing at a missing category): rolled back.
            return@withLock RestoreResult.NotABackup
        }
        // Once committed, finish even if the caller goes away: otherwise posted notifications
        // would keep ids that now belong to other transactions.
        withContext(NonCancellable) {
            events.dataReplaced()
            settings.restore(file.settings.toSnapshot())
            // Seed groups newer than the backup are added (or adopted by name).
            settings.setSeededCategoriesVersion(file.seedVersion)
            seeder.ensureSeeded()
            // SMS that arrived after the backup was made come back through the next scan.
            checkpoint.rewindTo(file.exportedAt)
            sync.reconcileNow()
        }
        RestoreResult.Restored(file.transactions.size, file.categories.size)
    }

    private suspend fun replaceAll(file: BackupFile) {
        val dao = db.backupDao()
        clearTransactions()
        dao.deleteRules()
        dao.deleteAliases()
        dao.deleteAccounts()
        dao.deleteSubcategories()
        dao.deleteCategories()
        // Parents before children, and duplicate links after every transaction exists.
        dao.insertCategories(file.categories.sortedBy { it.parentId != null }.map { it.toEntity() })
        dao.insertAccounts(file.accounts.map { it.toEntity() })
        dao.insertTransactions(file.transactions.map { it.toEntity().copy(duplicateOfId = null) })
        file.transactions.forEach { t -> t.duplicateOfId?.let { dao.setDuplicateOf(t.id, it) } }
        dao.insertRules(file.merchantRules.map { it.toEntity() })
        dao.insertAliases(file.merchantAliases.map { it.toEntity() })
        dao.insertDayCloses(file.dayCloses.map { it.toEntity() })
    }

    /**
     * "Start fresh": deletes every transaction, the unparsed list and the closed days, and stops
     * the inbox scan from importing any SMS received before now. Categories, merchant rules,
     * names and accounts stay, so sorting keeps its memory. Returns how many transactions went.
     */
    suspend fun startFresh(zone: ZoneId = ZoneId.systemDefault()): Int = mutex.withLock {
        val now = clock.nowMillis()
        // The floor goes first: on its own it is harmless, and no scan can slip old SMS in after the delete.
        checkpoint.startAt(now)
        val removed = pipeline.replacingData {
            db.withTransaction {
                val count = db.backupDao().transactions().size
                clearTransactions()
                count
            }
        }
        withContext(NonCancellable) {
            events.dataReplaced()
            settings.setDayTrackingSince(Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay())
        }
        removed
    }

    private suspend fun clearTransactions() {
        val dao = db.backupDao()
        dao.deleteTransactions()
        dao.deleteDayCloses()
        dao.deleteUnparsed()
    }

    /**
     * Writes every transaction as CSV (RFC 4180, UTF-8, newest first) to [output] (not closed).
     * Amounts are signed rupees: money out is negative. SMS bodies are left out.
     */
    suspend fun exportCsv(output: OutputStream, zone: ZoneId = ZoneId.systemDefault()) = mutex.withLock {
        val (transactions, categories, accounts) = db.withTransaction {
            val dao = db.backupDao()
            Triple(dao.transactions(), dao.categories().associateBy { it.id }, dao.accounts().associateBy { it.id })
        }
        withContext(Dispatchers.IO) {
            val writer = output.bufferedWriter(Charsets.UTF_8)
            writer.csvRow(CSV_HEADER)
            transactions.sortedByDescending {
                it.occurredAt
            }.forEach { writer.csvRow(csvRow(it, categories, accounts, zone)) }
            writer.flush()
        }
    }

    private fun csvRow(
        t: TransactionEntity,
        categories: Map<Long, CategoryEntity>,
        accounts: Map<Long, AccountEntity>,
        zone: ZoneId,
    ): List<String> {
        val at = Instant.ofEpochMilli(t.occurredAt).atZone(zone)
        val leaf = t.categoryId?.let(categories::get)
        val parent = leaf?.parentId?.let(categories::get)
        val account = t.accountId?.let(accounts::get)
        val rupees = BigDecimal.valueOf(t.amountPaise, 2).let { if (t.direction == Direction.OUT) it.negate() else it }
        return listOf(
            at.format(DateTimeFormatter.ISO_LOCAL_DATE),
            at.format(TIME),
            rupees.toPlainString(),
            t.direction.name,
            t.status.name,
            text(parent?.name ?: leaf?.name),
            text(leaf?.name?.takeIf { parent != null }),
            text(t.merchantDisplay),
            text(account?.let { it.nickname ?: listOfNotNull(it.bankName, it.last4).joinToString(" ") }),
            text(t.note),
            t.source.name,
            text(t.reference),
        )
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val CSV_HEADER = listOf(
            "date", "time", "amount_inr", "direction", "status", "category", "subcategory", "merchant", "account",
            "note", "source", "reference",
        )

        /** Spreadsheets run cells starting with these as formulas; a leading quote keeps them text. */
        const val FORMULA_START = "=+-@\t\r"

        fun text(value: String?): String = when {
            value.isNullOrEmpty() -> ""
            value.first() in FORMULA_START -> "'$value"
            else -> value
        }

        fun Writer.csvRow(cells: List<String>) {
            write(
                cells.joinToString(",") { cell ->
                    if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                        "\"" + cell.replace("\"", "\"\"") + "\""
                    } else {
                        cell
                    }
                },
            )
            write("\r\n")
        }
    }
}
