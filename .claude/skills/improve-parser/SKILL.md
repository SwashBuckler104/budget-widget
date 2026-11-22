---
name: improve-parser
description: Turn the owner's bank SMS feedback in docs/parser-dataset.md (masked parser reports, appended over time) into parser fixes with golden tests. Use when asked to improve the parser, go through the parser dataset or feedback, or handle new unparsed bank SMS.
---

# Improve the parser from feedback

The owner pastes "Help improve the parser" reports (masked bank SMS the app couldn't read) into
`docs/parser-dataset.md` and keeps appending. That file and its ledger are private and gitignored:
never commit them, never quote a message from them in a commit, PR, issue or upstream draft. Every
fixture you write uses made-up values in the same template.

Read first: `CLAUDE.md`, `docs/PARSER_IMPROVEMENT.md`, `ingest/src/test/resources/golden/README.md`,
and in `docs/DECISIONS.md` ADR-0017 (sender rules), ADR-0028 (unknown senders), ADR-0042 (transfers),
ADR-0046 (masking) and ADR-0061 (local formats, this loop).

## 1. Find what is new

```sh
python3 -I scripts/feedback-to-golden.py docs/parser-dataset.md --dry-run
```

It reads every report in the file (1.0.2 text blocks and JSON Lines, in any order) and prints how
many messages are new. Messages already handled are in `docs/parser-dataset.md.ledger`. If nothing
is new, say so and stop. Refused messages still look unmasked: tell the owner, don't mask them
yourself into fixtures.

## 2. Decide what each new message is

Group the new messages by sender and template, then decide by reading the text (never by running
the parser) which of these it is:

| What | Where the fix goes |
|---|---|
| A supported bank (`golden/<bank>.txt` exists) whose wording upstream misses or misreads | `FlowRules.kt` (meaning: transfer, loan, card bill), `ParsedTransactionMapper.kt` (field clean-up); a `docs/upstream/` draft |
| A bank or wallet no upstream parser knows | a new entry in `LocalFormats.kt` (TRAI header core plus exact wording), a new `golden/<name>.txt`, its route in `BANKS` in `scripts/feedback-to-golden.py`, and `GoldenCorpus.BANK_FILES` |
| Not money in an account (shop credit, points, offers, reminders, OTPs, declined payments) | `FinancialSmsHeuristic.kt`, so it is never kept; a `FinancialSmsHeuristicTest` case |
| Real but impossible to record correctly (foreign currency: v1 is INR only) | leave it `UNPARSED` with a golden case and a comment why |

Rules that hold for every fix:
- Never edit `third_party/`. Only `:ingest` may import `com.pennywiseai.*`.
- Upstream stays the parser for every sender it knows. A local format only claims senders with no
  upstream INR parser, under the same sender rules (no phone numbers, no `-P` headers).
- Narrow: match the sender header and the exact wording. Each rule or format gets golden cases that
  fail without it, at least 2 transaction cases per new golden file.
- Money is `Long` paise, converted only through `ParsedTransactionMapper.toPaiseOrNull`.
- Wallet or account credits that may be a top-up or a refund are `TRANSFER` in with no flow, so they
  stay in the inbox for the user. Debits are `EXPENSE`.
- Masked words: the report masks words outside `RedactorVocabulary` (ADR-0046). Don't add brand or
  merchant words to it; make the pattern accept the masked word when the sender already pins it.

## 3. Write the cases, then the fix

1. Write golden cases with made-up values in the same template (names, accounts, amounts, balances,
   phone and reference numbers all fake; keep the sender and the wording). Expected values come
   from reading the text.
2. Run the golden tests and watch the new cases fail:
   `scripts/capped.sh ./gradlew :ingest:testDebugUnitTest --tests '*Golden*'`
3. Write the fix, then run the whole set until green:
   `scripts/capped.sh ./gradlew spotlessApply` and
   `scripts/capped.sh ./gradlew :ingest:testDebugUnitTest :core:data:testDebugUnitTest :parser-core:test`
   (`SmsRedactorTest` also checks that a masked report of each golden message still parses).
4. Draft or extend `docs/upstream/000N-*.md` for anything that belongs in PennyWise.
5. Record the handled messages: `python3 -I scripts/feedback-to-golden.py docs/parser-dataset.md --mark-done`.
   (Or, to work from stubs instead of writing cases by hand, run it without flags: it appends
   `type: TODO` stubs holding the masked text, which you then replace with made-up values.)

## 4. Commit and report

- One commit per bank or format (Conventional Commits, e.g. `feat(ingest): read NKGSB Bank alerts`),
  plus an ADR line in `docs/DECISIONS.md` when a new kind of rule appears. No co-author lines, no
  em dashes, no private data.
- Tell the owner, per sender: what it is now (expense, income, transfer left in the inbox, ignored,
  kept unparsed) and why, plus anything that needs their call.
