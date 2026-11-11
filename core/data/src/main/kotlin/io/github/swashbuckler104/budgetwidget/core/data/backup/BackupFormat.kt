package io.github.swashbuckler104.budgetwidget.core.data.backup

import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.DayCloseEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantAliasEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.AppearanceSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NudgeSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsSnapshot
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import kotlinx.serialization.Serializable

/**
 * The backup file (SCOPE.md F9, docs/DECISIONS.md ADR-0027): plain JSON with its own versioned
 * schema, decoupled from the Room entities so a database migration never silently changes it.
 * Enums are stored by name. Money is paise, times are epoch ms (UTC).
 */
@Serializable
internal data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val appVersion: String,
    /** The seed category set the categories were based on, so newer seed groups are added on restore. */
    val seedVersion: Int,
    val categories: List<BackupCategory>,
    val accounts: List<BackupAccount>,
    val transactions: List<BackupTransaction>,
    val merchantRules: List<BackupRule>,
    val merchantAliases: List<BackupAlias> = emptyList(),
    val dayCloses: List<BackupDayClose> = emptyList(),
    val settings: BackupSettings,
) {
    companion object {
        const val FORMAT = "budget-widget-backup"

        /** Bump on any incompatible change and keep reading older versions. */
        const val VERSION = 1
    }
}

@Serializable
internal data class BackupCategory(
    val id: Long,
    val parentId: Long? = null,
    val name: String,
    val emoji: String,
    val colorArgb: Int,
    val kind: CategoryKind,
    val sortOrder: Int,
    val archived: Boolean = false,
    val isSeed: Boolean = false,
    val seedKey: String? = null,
)

@Serializable
internal data class BackupAccount(
    val id: Long,
    val bankName: String,
    val last4: String? = null,
    val nickname: String? = null,
    val kind: AccountKind,
    val lastBalancePaise: Long? = null,
    val hidden: Boolean = false,
    val createdAt: Long,
)

@Serializable
internal data class BackupTransaction(
    val id: Long,
    val txnHash: String? = null,
    val source: TransactionSource,
    val amountPaise: Long,
    val direction: Direction,
    val parserType: ParserType? = null,
    val merchantRaw: String? = null,
    val merchantDisplay: String? = null,
    val merchantKey: String? = null,
    val accountId: Long? = null,
    val occurredAt: Long,
    val smsSender: String? = null,
    val smsBody: String? = null,
    val reference: String? = null,
    val balanceAfterPaise: Long? = null,
    val isCard: Boolean = false,
    val status: TransactionStatus,
    val ignoreReason: IgnoreReason? = null,
    val categoryId: Long? = null,
    val autoCategorized: Boolean = false,
    val isBackfill: Boolean = false,
    val note: String? = null,
    val duplicateOfId: Long? = null,
    val editedByUser: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val sortedAt: Long? = null,
)

@Serializable
internal data class BackupRule(
    val merchantKey: String,
    val categoryId: Long,
    val confirmCount: Int,
    val lastUsedAt: Long,
)

@Serializable
internal data class BackupAlias(val merchantKey: String, val displayName: String)

@Serializable
internal data class BackupDayClose(val date: String, val pendingAtMidnight: Int, val closed: Boolean)

@Serializable
internal data class BackupSettings(
    val autoSort: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notifyMinAmountPaise: Long = 0,
    val nudgeEnabled: Boolean = true,
    val nudgeMinuteOfDay: Int = NudgeSettings.DEFAULT_NUDGE_MINUTE,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

internal fun CategoryEntity.toBackup() =
    BackupCategory(id, parentId, name, iconKey, colorArgb, kind, sortOrder, archived, isSeed, seedKey)

internal fun BackupCategory.toEntity() =
    CategoryEntity(id, parentId, name, emoji, colorArgb, kind, sortOrder, archived, isSeed, seedKey)

internal fun AccountEntity.toBackup() =
    BackupAccount(id, bankName, last4, nickname, kind, lastBalancePaise, hidden, createdAt)

internal fun BackupAccount.toEntity() =
    AccountEntity(id, bankName, last4, nickname, kind, lastBalancePaise, hidden, createdAt)

internal fun TransactionEntity.toBackup() = BackupTransaction(
    id, txnHash, source, amountPaise, direction, parserType, merchantRaw, merchantDisplay, merchantKey, accountId,
    occurredAt, smsSender, smsBody, reference, balanceAfterPaise, isCard, status, ignoreReason, categoryId,
    autoCategorized, isBackfill, note, duplicateOfId, editedByUser, createdAt, updatedAt, sortedAt,
)

internal fun BackupTransaction.toEntity() = TransactionEntity(
    id, txnHash, source, amountPaise, direction, parserType, merchantRaw, merchantDisplay, merchantKey, accountId,
    occurredAt, smsSender, smsBody, reference, balanceAfterPaise, isCard, status, ignoreReason, categoryId,
    autoCategorized, isBackfill, note, duplicateOfId, editedByUser, createdAt, updatedAt, sortedAt,
)

internal fun MerchantRuleEntity.toBackup() = BackupRule(merchantKey, categoryId, confirmCount, lastUsedAt)

internal fun BackupRule.toEntity() = MerchantRuleEntity(merchantKey, categoryId, confirmCount, lastUsedAt)

internal fun MerchantAliasEntity.toBackup() = BackupAlias(merchantKey, displayName)

internal fun BackupAlias.toEntity() = MerchantAliasEntity(merchantKey, displayName)

internal fun DayCloseEntity.toBackup() = BackupDayClose(date, pendingAtMidnight, closed)

internal fun BackupDayClose.toEntity() = DayCloseEntity(date, pendingAtMidnight, closed)

internal fun SettingsSnapshot.toBackup() = BackupSettings(
    autoSort = autoSort,
    notificationsEnabled = notifications.enabled,
    notifyMinAmountPaise = notifications.minAmountPaise,
    nudgeEnabled = nudge.enabled,
    nudgeMinuteOfDay = nudge.minuteOfDay,
    theme = appearance.theme,
    dynamicColor = appearance.dynamicColor,
)

internal fun BackupSettings.toSnapshot() = SettingsSnapshot(
    autoSort = autoSort,
    notifications = NotificationSettings(notificationsEnabled, notifyMinAmountPaise),
    nudge = NudgeSettings(nudgeEnabled, nudgeMinuteOfDay),
    appearance = AppearanceSettings(theme, dynamicColor),
)
