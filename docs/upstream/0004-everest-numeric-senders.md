# Draft upstream issue: Everest Bank parser claims every 7-10 digit sender

Status: draft, not submitted. Upstream: sarim2000/pennywiseai-tracker at `0623c2b`. Found in our
emulator end-to-end test. Budget Widget works around it in `PennyWiseSmsParser` (ADR-0017).

---

**Title:** `fix(parser-core): EverestBankParser.canHandle matches personal phone numbers`

**Body:**

`EverestBankParser.canHandle` returns true for `^\d{7,10}$`, so
`BankParserFactory.isKnownBankSender("9812345678")` is true. Indian phones often show a contact's
number as 10 digits without `+91`. So every personal SMS from such a contact counts as a bank SMS,
and apps that store unparsed bank messages store personal texts. A personal message that happens
to read like a debit is parsed as an NPR transaction.

Suggested fix: limit the numeric rule to Everest's actual short codes, or require an Everest
keyword in the body for numeric senders. Also, `contains("EBL")` matches unrelated headers.
