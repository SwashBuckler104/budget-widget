package io.github.swashbuckler104.budgetwidget.ingest.receiver

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import org.junit.Test

class SmsAssemblerTest {
    @Test
    fun `multipart parts are joined in order without separators`() {
        val parts = listOf(
            SmsPart("AD-HDFCBK", "Rs.500.00 debited from A/c XX1234 ", 10),
            SmsPart("AD-HDFCBK", "on 20-Oct-25 to merchant@upi", 11),
        )
        assertThat(SmsAssembler.assemble(parts)).containsExactly(
            RawSms("AD-HDFCBK", "Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi", 10),
        )
    }

    @Test
    fun `parts from different senders stay separate`() {
        val parts = listOf(SmsPart("A", "1", 1), SmsPart("B", "2", 2), SmsPart("A", "3", 3))
        assertThat(SmsAssembler.assemble(parts)).containsExactly(RawSms("A", "13", 1), RawSms("B", "2", 2))
    }

    @Test
    fun `parts without sender or body are dropped`() {
        assertThat(
            SmsAssembler.assemble(listOf(SmsPart(null, "x", 1), SmsPart(" ", "x", 1), SmsPart("A", null, 1))),
        ).isEmpty()
    }
}
