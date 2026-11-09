package io.github.swashbuckler104.budgetwidget.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.swashbuckler104.budgetwidget.core.database.dao.AccountDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.CategoryDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.MerchantRuleDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.TransactionDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.UnparsedSmsDao
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.DayCloseEntity
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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 1 -> 2: category.seedKey (nullable) with a unique index.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun transactionDao(): TransactionDao

    abstract fun unparsedSmsDao(): UnparsedSmsDao

    abstract fun categoryDao(): CategoryDao

    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        const val NAME = "budget.db"
    }
}
