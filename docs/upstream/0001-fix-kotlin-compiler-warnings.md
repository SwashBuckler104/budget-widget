# Draft upstream PR: fix Kotlin compiler warnings in parser-core

Status: draft, not submitted. Upstream: sarim2000/pennywiseai-tracker, observed at `0623c2b`
with Kotlin 2.4.21 (upstream uses the same Kotlin version).

---

**Title:** `fix(parser-core): resolve compiler warnings (redundant !!, dead elvis, deprecated Instant)`

**Body:**

Compiling `parser-core` with Kotlin 2.4.21 reports these warnings:

| File | Line | Warning |
|---|---|---|
| `bank/HDFCBankParser.kt` | 645, 716 | Unnecessary non-null assertion (`!!`) on non-null `BigDecimal` |
| `bank/SBIBankParser.kt` | 684 | Unnecessary non-null assertion (`!!`) on non-null `BigDecimal` |
| `bank/KTCCreditCardParser.kt` | 26 | Elvis `parsed.type ?: TransactionType.CREDIT` always returns the left operand |
| `bank/SouthIndianBankParser.kt` | 69 | `kotlinx.datetime.Instant` typealias is deprecated in favour of `kotlin.time.Instant` |
| test `BankSamplesDocTest.kt` | 215 | Elvis always returns the left operand (`String`) |
| test `TestOpayBankParser.kt` | 70 | Test name contains characters that cause problems on Windows |

The `KTCCreditCardParser` one may be a real bug rather than noise. `type` is non-null, so the
`CREDIT` fallback never applies. If the intent is "card transactions are always CREDIT", it should
be `type = TransactionType.CREDIT`. If the intent is "keep the parsed type", the elvis should go.
A test pinning the expected type would settle it.

The other changes have no behaviour change. Removing the `!!` / dead elvis and switching to
`kotlin.time.Instant` keeps the build warning-free, which helps
downstream consumers who compile with `-Werror`.
