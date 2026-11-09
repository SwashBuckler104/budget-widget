package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason

/**
 * A message from a known bank sender that did not become a transaction, kept so the user can
 * report parser bugs (SCOPE.md F2). Capped at `UnparsedSmsDao.MAX_ROWS` rows. [receivedAt] is
 * epoch ms (UTC).
 */
@Entity(
    tableName = "unparsed_sms",
    indices = [Index(value = ["bodyHash"], unique = true), Index("receivedAt")],
)
data class UnparsedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    /** SHA-256 of the body, hex. */
    val bodyHash: String,
    val body: String?,
    val receivedAt: Long,
    val reason: UnparsedReason,
)
