package io.github.swashbuckler104.budgetwidget.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema version must migrate to the latest without losing data (SCOPE.md §5). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BudgetDatabase::class.java)

    @Test
    fun `1 to 2 keeps transactions and categories and adds seedKey`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO account (id, bankName, last4, kind, hidden, createdAt) VALUES (1, 'HDFC Bank', '1234', 'BANK', 0, 0)",
            )
            db.execSQL(
                "INSERT INTO category (id, parentId, name, iconKey, colorArgb, kind, sortOrder, archived, isSeed) VALUES (7, NULL, 'Food', '🍽️', 0, 'EXPENSE', 0, 0, 0)",
            )
            db.execSQL(
                """
                INSERT INTO transactions (id, txnHash, source, amountPaise, direction, accountId, occurredAt, isCard, status,
                  categoryId, autoCategorized, isBackfill, editedByUser, createdAt, updatedAt)
                VALUES (1, 'h1', 'SMS', 15000, 'OUT', 1, 1000, 0, 'CATEGORIZED', 7, 0, 0, 0, 1, 1)
                """,
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT amountPaise, categoryId FROM transactions WHERE txnHash = 'h1'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(15000)
                assertThat(it.getLong(1)).isEqualTo(7)
            }
            db.query("SELECT name, seedKey FROM category WHERE id = 7").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("Food")
                assertThat(it.isNull(1)).isTrue()
            }
        }
    }

    @Test
    fun `2 to 3 adds sortedAt and fills it for sorted rows only`() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO category (id, parentId, name, iconKey, colorArgb, kind, sortOrder, archived, isSeed) VALUES (7, NULL, 'Food', '🍽️', 0, 'EXPENSE', 0, 0, 0)",
            )
            listOf(
                "('a', 'CATEGORIZED', 7, 500)",
                "('b', 'IGNORED', NULL, 600)",
                "('c', 'PENDING', NULL, 700)",
            ).forEach {
                db.execSQL(
                    """
                    INSERT INTO transactions (txnHash, status, categoryId, updatedAt, source, amountPaise, direction,
                      occurredAt, isCard, autoCategorized, isBackfill, editedByUser, createdAt)
                    VALUES ${it.dropLast(1)}, 'SMS', 100, 'OUT', 1, 0, 0, 0, 0, 1)
                    """,
                )
            }
        }

        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT txnHash, sortedAt FROM transactions ORDER BY txnHash").use {
                val rows =
                    buildMap { while (it.moveToNext()) put(it.getString(0), if (it.isNull(1)) null else it.getLong(1)) }
                assertThat(rows).containsExactly("a", 500L, "b", 600L, "c", null)
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
