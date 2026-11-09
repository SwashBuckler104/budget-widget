package io.github.swashbuckler104.budgetwidget.ingest.parser

import com.google.common.truth.Truth.assertWithMessage
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import java.math.BigDecimal
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Our anonymized SMS corpus for the seven v1 banks, through the real upstream parser and our
 * mapper. This gates parser upgrades (SCOPE.md §5, docs/PARSER_UPGRADE.md).
 */
@RunWith(Parameterized::class)
class GoldenSmsTest(@Suppress("unused") private val name: String, private val case: GoldenCase) {
    private val parser = PennyWiseSmsParser()

    @Test
    fun matchesExpectation() {
        val sms = RawSms(case.required("sender"), case.required("body"), RECEIVED_AT)
        val type = case.required("type")
        val check = { what: String -> assertWithMessage("${case.name}: $what") }

        if (type == "NOT_BANK") {
            check("known bank sender").that(parser.isKnownBankSender(sms.sender)).isFalse()
            return
        }
        check("known bank sender").that(parser.isKnownBankSender(sms.sender)).isTrue()
        val result = parser.parse(sms)

        when (type) {
            "UNPARSED" -> check("result").that(result).isInstanceOf(SmsParseResult.Unparsed::class.java)

            "BALANCE" -> {
                check("result").that(result).isInstanceOf(SmsParseResult.BalanceUpdate::class.java)
                result as SmsParseResult.BalanceUpdate
                check("balance").that(result.balancePaise).isEqualTo(paise(case.required("balance")))
                case.fields["last4"]?.let { check("last4").that(result.accountLast4).isEqualTo(it.orNull()) }
            }

            else -> {
                check("result").that(result).isInstanceOf(SmsParseResult.Transaction::class.java)
                val parsed = (result as SmsParseResult.Transaction).parsed
                check("type").that(parsed.parserType).isEqualTo(ParserType.valueOf(type))
                check("amount (paise)").that(parsed.amountPaise).isEqualTo(paise(case.required("amount")))
                check("direction").that(parsed.direction).isEqualTo(Direction.valueOf(case.required("direction")))
                val knownBug = case.fields["merchant-upstream-bug"]
                if (knownBug != null) {
                    check("merchant (known upstream bug; if this fails, upstream fixed it: drop merchant-upstream-bug)")
                        .that(parsed.merchant).isEqualTo(knownBug)
                } else {
                    case.fields["merchant"]?.let { check("merchant").that(parsed.merchant).isEqualTo(it.orNull()) }
                }
                case.fields["last4"]?.let { check("last4").that(parsed.accountLast4).isEqualTo(it.orNull()) }
                case.fields["card"]?.let { check("card").that(parsed.isCard).isEqualTo(it.toBooleanStrict()) }
                case.fields["balance"]?.let { check("balance").that(parsed.balanceAfterPaise).isEqualTo(paise(it)) }
                check("body kept verbatim").that(parsed.body).isEqualTo(sms.body)
                check("occurredAt").that(parsed.occurredAt).isEqualTo(RECEIVED_AT)
            }
        }
    }

    private fun String.orNull(): String? = takeUnless { it == "-" }

    private fun paise(rupees: String): Long = BigDecimal(rupees).movePointRight(2).longValueExact()

    companion object {
        private const val RECEIVED_AT = 1_793_000_000_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val all = GoldenCorpus.BANK_FILES.flatMap { bank ->
                val cases = GoldenCorpus.load(bank)
                val transactions = cases.count { it.fields["type"] !in setOf("UNPARSED", "NOT_BANK", "BALANCE") }
                check(transactions >= 2) { "golden/$bank.txt needs at least 2 transaction cases, has $transactions" }
                cases
            }
            return all.map { arrayOf(it.name, it) }
        }
    }
}
