package io.github.swashbuckler104.budgetwidget.ingest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.ingest.work.IngestWorkScheduler
import javax.inject.Inject

/**
 * Real-time path (SCOPE.md F2). `SMS_RECEIVED` is exempt from implicit-broadcast limits, and the
 * manifest requires `BROADCAST_SMS`, so only the telephony stack can send it. The receiver does no
 * I/O: it filters to bank senders (so personal SMS never reaches WorkManager's database) and hands
 * each message to an expedited worker.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var parser: SmsParser

    @Inject
    internal lateinit var scheduler: IngestWorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.filterNotNull()
            ?.map { SmsPart(it.displayOriginatingAddress, it.displayMessageBody, it.timestampMillis) }
            .orEmpty()
        SmsAssembler.assemble(parts)
            // Same rule as the pipeline's step 1: known banks, plus bank-like alerts from senders
            // no parser knows yet (ADR-0028), so those reach the unparsed list right away.
            .filter { parser.isKnownBankSender(it.sender) || parser.looksLikeUnknownBankAlert(it) }
            .forEach(scheduler::enqueueIngest)
    }
}
