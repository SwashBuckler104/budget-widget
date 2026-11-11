package io.github.swashbuckler104.budgetwidget.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.swashbuckler104.budgetwidget.core.database.dao.AccountDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.BackupDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.CategoryDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.DayCloseDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.MerchantAliasDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.MerchantRuleDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.TransactionDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.UnparsedSmsDao
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.DayCloseEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantAliasEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity

/**
 * All SCOPE.md §5 tables exist from version 1. Every version bump needs a migration and a
 * migration test (`MigrationTest`); schemas are exported to `core/database/schemas/`.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantRuleEntity::class,
        DayCloseEntity::class,
        UnparsedSmsEntity::class,
        MerchantAliasEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // 1 -> 2: category.seedKey (nullable) with a unique index.
        AutoMigration(from = 1, to = 2),
        // 2 -> 3: transactions.sortedAt (nullable), filled in for rows already sorted.
        AutoMigration(from = 2, to = 3, spec = BudgetDatabase.SortedAtSpec::class),
        // 3 -> 4: merchant_alias table (names the user gave merchants).
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun transactionDao(): TransactionDao

    abstract fun unparsedSmsDao(): UnparsedSmsDao

    abstract fun categoryDao(): CategoryDao

    abstract fun merchantRuleDao(): MerchantRuleDao

    abstract fun dayCloseDao(): DayCloseDao

    abstract fun merchantAliasDao(): MerchantAliasDao

    abstract fun backupDao(): BackupDao

    /** Rows sorted before v3 have no sort time; their last update is the best estimate. */
    class SortedAtSpec : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE transactions SET sortedAt = updatedAt WHERE status != 'PENDING'")
        }
    }

    companion object {
        const val NAME = "budget.db"
    }
}
