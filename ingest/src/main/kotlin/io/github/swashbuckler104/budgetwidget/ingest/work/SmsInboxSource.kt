package io.github.swashbuckler104.budgetwidget.ingest.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import javax.inject.Inject

/** Read access to the system SMS inbox. */
internal interface SmsInboxSource {
    fun canRead(): Boolean

    /** Calls [action] for each inbox message received after [afterMillis] (epoch ms, UTC), oldest first. */
    suspend fun forEachSince(afterMillis: Long, action: suspend (RawSms) -> Unit)
}

internal class ContentResolverSmsInbox @Inject constructor(@ApplicationContext private val context: Context) :
    SmsInboxSource {
    override fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    override suspend fun forEachSince(afterMillis: Long, action: suspend (RawSms) -> Unit) {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(afterMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val sender = cursor.getString(address) ?: continue
                val text = cursor.getString(body) ?: continue
                action(RawSms(sender, text, cursor.getLong(date)))
            }
        }
    }
}
