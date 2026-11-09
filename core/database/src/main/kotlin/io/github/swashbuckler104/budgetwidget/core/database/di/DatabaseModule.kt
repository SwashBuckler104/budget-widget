package io.github.swashbuckler104.budgetwidget.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.dao.AccountDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.CategoryDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.MerchantRuleDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.TransactionDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.UnparsedSmsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): BudgetDatabase =
        Room.databaseBuilder(context, BudgetDatabase::class.java, BudgetDatabase.NAME).build()

    @Provides
    fun accountDao(db: BudgetDatabase): AccountDao = db.accountDao()

    @Provides
    fun transactionDao(db: BudgetDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun unparsedSmsDao(db: BudgetDatabase): UnparsedSmsDao = db.unparsedSmsDao()

    @Provides
    fun categoryDao(db: BudgetDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun merchantRuleDao(db: BudgetDatabase): MerchantRuleDao = db.merchantRuleDao()
}
