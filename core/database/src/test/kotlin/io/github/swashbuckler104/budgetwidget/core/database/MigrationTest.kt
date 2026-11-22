package io.github.swashbuckler104.budgetwidget.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.CategoryPalette
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

    @Test
    fun `3 to 4 adds an empty merchant_alias table and keeps transactions`() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO transactions (txnHash, status, updatedAt, source, amountPaise, direction, occurredAt,
                  isCard, autoCategorized, isBackfill, editedByUser, createdAt)
                VALUES ('a', 'PENDING', 1, 'SMS', 100, 'OUT', 1, 0, 0, 0, 0, 1)
                """,
            )
        }

        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            db.query("SELECT COUNT(*) FROM transactions").use {
                it.moveToFirst()
                assertThat(it.getInt(0)).isEqualTo(1)
            }
            db.execSQL("INSERT INTO merchant_alias (merchantKey, displayName) VALUES ('swiggy', 'Swiggy')")
            db.query("SELECT displayName FROM merchant_alias WHERE merchantKey = 'swiggy'").use {
                it.moveToFirst()
                assertThat(it.getString(0)).isEqualTo("Swiggy")
            }
        }
    }

    @Test
    fun `4 to 5 moves old category colors to the bright palette and keeps custom ones`() {
        val oldHousing = 0xFF5D4037.toInt()
        val custom = 0xFF112233.toInt()
        helper.createDatabase(DB, 4).use { db ->
            listOf(1 to oldHousing, 2 to custom).forEach { (id, color) ->
                db.execSQL(
                    "INSERT INTO category (id, parentId, name, iconKey, colorArgb, kind, sortOrder, archived, " +
                        "isSeed) VALUES ($id, NULL, 'C$id', '🙂', $color, 'EXPENSE', $id, 0, 0)",
                )
            }
        }

        helper.runMigrationsAndValidate(DB, 5, true).use { db ->
            db.query("SELECT id, colorArgb FROM category ORDER BY id").use {
                val colors = buildMap { while (it.moveToNext()) put(it.getLong(0), it.getInt(1)) }
                assertThat(colors).containsExactly(1L, CategoryPalette.upgrade(oldHousing), 2L, custom)
                assertThat(colors[1L]).isEqualTo(0xFFFFF176.toInt())
            }
        }
    }

    @Test
    fun `5 to 6 keeps every column of a v1_0_1 install and adds an empty linkedToId`() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO account (id, bankName, last4, nickname, kind, lastBalancePaise, hidden, createdAt) " +
                    "VALUES (1, 'HDFC Bank', '8812', 'Salary', 'BANK', 4875000, 0, 5)",
            )
            db.execSQL(
                "INSERT INTO category (id, parentId, name, iconKey, colorArgb, kind, sortOrder, archived, isSeed, " +
                    "seedKey) VALUES (7, NULL, 'My own', '🙂', -1, 'EXPENSE', 3, 0, 0, NULL)",
            )
            db.execSQL(
                """
                INSERT INTO transactions (id, txnHash, source, amountPaise, direction, parserType, merchantRaw,
                  merchantDisplay, merchantKey, accountId, occurredAt, smsSender, smsBody, reference,
                  balanceAfterPaise, isCard, status, ignoreReason, categoryId, autoCategorized, isBackfill, note,
                  duplicateOfId, editedByUser, createdAt, updatedAt, sortedAt)
                VALUES (1, 'h1', 'SMS', 1250000, 'OUT', 'INVESTMENT', 'raw', 'Shop', 'shop', 1, 1000, 'AD-HDFCBK',
                  'body', '630199887766', 4875000, 0, 'CATEGORIZED', NULL, 7, 0, 0, 'n', NULL, 1, 2, 3, 4)
                """,
            )
            db.execSQL(
                "INSERT INTO merchant_rule (merchantKey, categoryId, confirmCount, lastUsedAt) VALUES ('shop', 7, 4, 9)",
            )
            db.execSQL("INSERT INTO day_close (date, pendingAtMidnight, closed) VALUES ('2026-11-14', 0, 1)")
        }

        helper.runMigrationsAndValidate(DB, 6, true).use { db ->
            db.query(
                "SELECT amountPaise, categoryId, note, reference, sortedAt, editedByUser, linkedToId FROM transactions",
            ).use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(1250000)
                assertThat(it.getLong(1)).isEqualTo(7)
                assertThat(it.getString(2)).isEqualTo("n")
                assertThat(it.getString(3)).isEqualTo("630199887766")
                assertThat(it.getLong(4)).isEqualTo(4)
                assertThat(it.getInt(5)).isEqualTo(1)
                assertThat(it.isNull(6)).isTrue()
            }
            db.query("SELECT name, colorArgb FROM category WHERE id = 7").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("My own")
                assertThat(it.getInt(1)).isEqualTo(-1)
            }
            db.query("SELECT nickname FROM account WHERE id = 1").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("Salary")
            }
            db.query("SELECT confirmCount FROM merchant_rule WHERE merchantKey = 'shop'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getInt(0)).isEqualTo(4)
            }
            db.query("SELECT COUNT(*) FROM day_close").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getInt(0)).isEqualTo(1)
            }
        }
    }

    @Test
    fun `6 to 7 keeps merchant rules and makes none of them always`() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO category (id, parentId, name, iconKey, colorArgb, kind, sortOrder, archived, isSeed, " +
                    "seedKey) VALUES (7, NULL, 'Food', '🍽️', -1, 'EXPENSE', 3, 0, 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO merchant_rule (merchantKey, categoryId, confirmCount, lastUsedAt) VALUES ('shop', 7, 4, 9)",
            )
        }

        helper.runMigrationsAndValidate(DB, 7, true).use { db ->
            db.query("SELECT categoryId, confirmCount, lastUsedAt, always FROM merchant_rule").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(7)
                assertThat(it.getInt(1)).isEqualTo(4)
                assertThat(it.getLong(2)).isEqualTo(9)
                assertThat(it.getInt(3)).isEqualTo(0)
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
