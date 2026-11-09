package io.github.swashbuckler104.budgetwidget.ingest.receiver

import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms

/** One PDU's worth of an incoming SMS. [timestampMillis] is epoch ms (UTC). */
internal data class SmsPart(val sender: String?, val body: String?, val timestampMillis: Long)

internal object SmsAssembler {
    /**
     * Joins the parts of multipart messages, per sender and in delivery order, with no separator
     * (the same text the SMS inbox stores, which keeps `txnHash` equal across both paths). The
     * message time is its first part's time. Parts without a sender or body are dropped.
     */
    fun assemble(parts: List<SmsPart>): List<RawSms> = parts
        .filter { !it.sender.isNullOrBlank() && it.body != null }
        .groupBy { it.sender!! }
        .map { (sender, group) ->
            RawSms(
                sender = sender,
                body = group.joinToString(separator = "") { it.body!! },
                receivedAt = group.first().timestampMillis,
            )
        }
}
