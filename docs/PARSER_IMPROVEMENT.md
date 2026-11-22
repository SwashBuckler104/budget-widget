# Improving the parser

How bank SMS the app can't read become parser fixes. There is no model in the app: bank SMS are
template-like, so reviewed rules plus test cases work better and keep the app small, offline and
auditable (docs/handoff/PLAN.md, section B). The "model" is this loop, run by a developer with a
coding agent.

```
phone: Settings > Help improve the parser  ->  email / file (masked)
dev:   scripts/feedback-to-golden.py       ->  golden stubs (type: TODO)
agent: fill expected values, write rules   ->  tests green
dev:   review, commit, release
```

## 1. Collect

On the phone: *Settings → Help improve the parser → Scan last 90 days*. The scan runs in memory and
changes no budget data (ADR-0043). It keeps only messages that read like bank transactions but
were not parsed, masks them (ADR-0046: digits become 9 except the last 2 of account and card
numbers, words outside the built-in vocabulary become x of the same length, links and UPI IDs
become `<URL>` and `<UPI>`), and shows exactly what will be sent. *Keep real amounts* leaves
transaction amounts readable. Remove single messages, or every message from one sender with
*Remove all*, then *Email* (your own mail app sends it) or *Save file*.

The report is JSON Lines (ADR-0047), one self-contained object per message:

```
{"v":2,"app":"1.0.3","parser":"0623c2b","sender":"ATMSBI","reason":"NO_MATCH","count":2,"month":"2026-11","body":"..."}
```

The saved `.jsonl` file is exactly that. The email body has one line of explanation above the same
lines. Reports from several phones can be concatenated into one file. Prefer the saved file: a mail
app that hard-wraps long lines can drop a space where it wrapped (the script joins wrapped lines
back, but can't know about a lost space).

Masked text keeps its shape, so templates still read: `Rs.9,999.99 debited from A/c XX9912 to
XXXXXX on 99-Nov-99`. Expected values in golden stubs come from the masked text (amount 9999.99,
last4 `9912`, merchant `XXXXXX`); that is fine, the template is what the parser learns.

Single messages can also be copied from *Settings → Unparsed bank SMS*. Those are masked less
(numbers only, so you can recognise them), so mask names and amounts by hand, then paste them into
a `.jsonl` file in the same format (see `ParserFeedbackReport` in
`core/data/.../feedback/ParserFeedback.kt`).

Never commit an unmasked message. Never paste real SMS into a public issue.

## 2. Turn the report into test stubs

```sh
python3 -I scripts/feedback-to-golden.py report.jsonl --dry-run   # see where each message goes
python3 -I scripts/feedback-to-golden.py report.jsonl             # append stubs
```

Each message becomes a case with `type: TODO` in `ingest/src/test/resources/golden/<bank>.txt`
(banks we don't ship go to `golden/unsupported.txt`, which the tests don't load). A message that
still has an `@`, or a run of 6+ digits that isn't all 9s apart from its last 2, is refused: mask
it by hand first. `GoldenSmsTest` fails
until every TODO is filled in, which is the point.

## 3. Let an agent write the rules

The owner collects reports in `docs/parser-dataset.md` (gitignored; append each new report). In a
Claude Code session, run the project skill **`/improve-parser`** (`.claude/skills/improve-parser/`):
it finds the new messages (the script keeps a local ledger, so each is handled once), decides what
each is, writes golden cases with made-up values, fixes our layer, including `LocalFormats.kt` for
banks and wallets parser-core doesn't know (ADR-0061), drafts upstream issues and runs the tests.

Without the skill, paste:

```
Read CLAUDE.md, docs/PARSER_IMPROVEMENT.md, ingest/src/test/resources/golden/README.md and
docs/DECISIONS.md ADR-0038, ADR-0042. In ingest/src/test/resources/golden/*.txt, fill in every case
with `type: TODO`: write a short name, then the expected type, amount, direction, merchant, last4
and flow by READING the SMS text (never by running the parser). Run
`scripts/capped.sh ./gradlew :ingest:testDebugUnitTest --tests '*Golden*'`. For each failing case,
fix it in our own layer, never in third_party/: FlowRules.kt for meaning (transfer, loan, card bill),
FinancialSmsHeuristic.kt for what is kept as unparsed, ParsedTransactionMapper.kt for field
clean-up, LocalFormats.kt for a sender no upstream parser knows (ADR-0061). If the bank's format is simply unsupported upstream, draft docs/upstream/000N-*.md
instead and mark the case UNPARSED with a comment linking the draft. Then run
`scripts/capped.sh ./gradlew :ingest:testDebugUnitTest :core:data:testDebugUnitTest :parser-core:test`.
One commit per bank, no unmasked data, no em dashes.
```

## 4. Review before committing

- Every expected value matches what the SMS says.
- No fixture holds a real name, account, card, UPI ID, phone or reference number.
- New rules are narrow (sender and wording), each with a golden case that would fail without it.
- `docs/upstream/` drafts exist for anything that belongs in PennyWise.

Upstream bumps follow [PARSER_UPGRADE.md](PARSER_UPGRADE.md); the golden corpus is the gate.
