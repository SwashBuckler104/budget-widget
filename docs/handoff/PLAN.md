# Implementation plan

For the implementing model. Written 2026-11-15 against `9d9e358` (v1.0.1, DB schema 5, backup
format 1). Evidence for every task is in [SECURITY_AUDIT.md](SECURITY_AUDIT.md) and
[TEST_REPORT.md](TEST_REPORT.md). Read `CLAUDE.md` and `docs/SCOPE.md` first; their hard
constraints still apply (no INTERNET, never edit `third_party/`, money is `Long` paise, warnings
are errors, tests first for logic, no em dash).

## Rules for every task

- One branch per task from `main`: `m9-<task-id>-<slug>` (for example `m9-e1-otp-retention`). Draft
  PR, the user merges.
- Bug fixes: reproduce first, as the user would see it (emulator `bw36`, or the phone with the
  user present and unlocked). Write the failing test, then fix.
- Before the PR: `scripts/capped.sh ./gradlew spotlessCheck lint test assembleDebug assembleRelease`
  (run with `TZ=UTC`), the four `scripts/check-*.sh`, then install on the emulator and do the
  task's device check. Stop the emulator before the full Gradle run (9G slice).
- Each schema change: Room migration + migration test, and bump `BackupFile.VERSION` when a backup
  must carry the new field.
- Each decision that changes behaviour: a short ADR in `docs/DECISIONS.md` (next is ADR-0038).
- Tick the checkbox here in the same PR.
- Do not touch the uncommitted `CLAUDE.md`, `scripts/capped.sh`, `config/systemd/` changes in the
  working tree; they are the user's.

## Updating the phone (the user's real data lives there)

The phone runs the **release-signed** v1.0.1 from GitHub Releases (certificate SHA-256
`e99e72ef41b71705f9cee601160de484346933b4c810ab6b8578e7abc2ccb0e7`, versionCode 3), with real
categories and a budget in progress.

- New versions reach the phone the way they reach every user: a GitHub release built by
  `release.yml` with the same key and a **higher versionCode**, installed over the old one. Data,
  categories, rules and widgets stay. For test builds use pre-release tags (`v1.1.0-rc1`, marked
  "pre-release" on GitHub); that needs `release.yml` to accept `-rcN` tags and mark them
  pre-release (small task, do it with E1).
- **Never** `adb install` a debug or locally signed APK on the phone: the signature differs, the
  install fails, and the only way to force it is an uninstall, which deletes the data. Never
  `adb uninstall`, `pm clear`, or run `:app:generateBaselineProfile` with the phone attached.
  Debug builds go to the emulator only.
- Every release that changes the DB schema needs a Room migration and a migration test from every
  shipped schema (5 today), plus an emulator upgrade test: install the previous release APK from
  GitHub, add data, install the new one over it, check the data.
- Before the user installs an update that migrates the schema, remind them to make a JSON backup
  (Settings -> Back up).
- "Detect and update automatically": the app itself cannot check for updates, because that needs
  the internet permission it deliberately does not have. Use an installer app that watches the
  GitHub releases: **Obtainium** now (add the repo URL once; it notifies and updates in place),
  later F-Droid / IzzyOnDroid or Play (section D). Add Obtainium steps and a one-tap
  "Add to Obtainium" link to the README install section (part of D7).

## Order

| # | Task | Size | Why this position |
|---|---|---|---|
| 1 | E1 OTP / non-financial bodies stored | S | privacy bug, breaks hard constraint 4 |
| 2 | A1 amount colours | S | user request, isolated |
| 3 | A2 bottom-sheet scaffold (jitter + easy dismiss) | M | user request, daily annoyance |
| 4 | B1 golden corpus extension | S | everything in B needs it |
| 5 | B2 rule layer around parser-core | M | fixes wrong types and merchants |
| 6 | B3 transaction linking | L | fixes double counting |
| 7 | E2 screen privacy, E3 log hygiene, E4 dex check | S | cheap hardening |
| 8 | C "Help improve the parser" | L | needs E1 and the redactor |
| 9 | B4 parser improvement loop (doc + script) | S | consumes C's export |
| 10 | E5 encrypted backup, E6 SMS body retention | M | |
| 11 | D Play Store + F-Droid readiness | M | mostly documents and release plumbing |
| 12 | E7 supply chain (verification metadata, provenance) | S | pairs with D reproducible build |
| - | B5 "Teach this message" | - | assessed, deferred (see B5) |

**Release split (owner, 2026-11-16).** 1.0.3 ships E5-E7, C2-C3, B5 lite and the design rework
(ADR-0051 to ADR-0054). Moved to **2.0.0 "Play Store ready"**: all of D, and the first round of
parser fixes from the owner's real unparsed messages (B4 loop run on their C3 report), which waits
until they have collected enough of them.

**2.0.0 scope (owner, 2026-11-22).** Ships the first parser feedback round and the
`improve-parser` skill (ADR-0061), the bottom sheet shake fix (ADR-0057), *All to Other* for an
imported history (ADR-0058), starting balances (ADR-0059) and the update check by hand-off
(ADR-0060). D (Play distribution) is not in this round: the owner schedules it.

---

## A. UI fixes

### [x] A1. Amount colours: outgoing red, income green everywhere, real signs (owner changed from neutral, ADR-0040)

**Goal.** No outgoing amount is red anywhere. Income stays green. Every directional amount shows
`+` or `−` (U+2212), so meaning never depends on colour.

**Files.**
- `core/model/.../Money.kt`: add `fun formatSignedInr(paise: Long, direction: Direction?): String`
  (the one place the sign is built). Unit test it.
- `core/designsystem/.../component/CommonComponents.kt:39-58` (`MoneyText`): remove the
  `emphasizeSpend` parameter; OUT uses `colorScheme.onSurface`.
- `core/designsystem/.../theme/Theme.kt:50-76`: delete `spend`, `spendContainer`,
  `onSpendContainer` from `MoneyColors` once nothing uses them.
- Callers: `feature/transactions/.../list/TransactionItems.kt:245`,
  `.../edit/TransactionSheets.kt:146`, `.../quicksort/QuickSortSheet.kt:174-179` (IN should use
  `BudgetTheme.money.income`, not `primary`).
- `widget/.../QuickSortWidgetContent.kt:209-212` and `WidgetTheme.kt:58-61`: OUT uses
  `GlanceTheme.colors.onSurface`; drop `WidgetMoney.spend`.
- `widget/.../InsightWidgetContent.kt:112` and `feature/analysis/.../AnalysisScreen.kt:520, 545`:
  "more than last month" and negative Net become neutral (`onSurface` / `surfaceVariant` chip)
  with an up arrow or `−`; "less than last month" and positive Net may stay green.
- `notifications/.../TransactionNotificationFactory.kt:41, 92, 122`: accent for OUT becomes the
  brand colour (or unset); title uses `formatSignedInr`.

**Keep red for:** errors, Delete, the Overdue badge. Those are not amounts.

**Acceptance.** `grep -rnE 'money\.spend|WidgetMoney\.spend|COLOR_OUT|emphasizeSpend'` over
non-test sources is empty. Unit test for `formatSignedInr` (IN, OUT, null, zero). Existing
screenshot or semantics tests updated.

**Device check.** Inject one debit and one credit (`ingest/src/test/resources/golden/hdfc.txt` has
formats that parse). Look at: inbox card, sorted row, edit sheet, Quick-Sort sheet, Analysis in
light and dark theme, both widgets, the notification. One screenshot each.

### [x] A2. One sheet scaffold (owner confirmed on the phone with 1.0.2-rc1: jitter gone): fixes the jitter, the easy dismiss, and five other sheets

**Root causes** (TEST_REPORT items 2, 3, 3b). Every sheet is a
`Column(Modifier.verticalScroll())` placed directly in a `ModalBottomSheet(skipPartiallyExpanded = true)`.
The sheet's nested-scroll connection takes the inner column's leftover drag and fling, and the
only other anchor is Hidden, so scrolling back to the top a little too hard closes the sheet.
Primary actions (Save) scroll away with the content. The jitter was **not reproduced on the
emulator**; its three candidate causes all come from the same structure.

**Step 0 (required).** With the user, reproduce the jitter on the phone: widget -> More -> scroll
slightly down -> release. Record it (`adb shell screenrecord`) and note: are "All categories"
open, how many categories, navigation mode, does it also happen from the in-app "Sort all". If it
does not reproduce there either, say so in the PR and continue; the scaffold is still right for
item 3.

**Approach.** Add `BudgetBottomSheet` to `core/designsystem/.../component/`:

```
ModalBottomSheet(sheetState, onDismissRequest, contentWindowInsets = default) {
  Column(Modifier.imePadding()) {              // insets handled once, here and by the sheet
    header()                                    // pinned: title + primary action (Save)
    Column(
      Modifier.weight(1f, fill = false)
        .nestedScroll(BlockSheetScroll)         // must come BEFORE verticalScroll
        .verticalScroll(scrollState)
    ) { body() }
    footer()                                    // pinned: Ignore / Skip, Cancel
  }
}
```

- `BlockSheetScroll` is a `NestedScrollConnection` whose `onPostScroll` and `onPostFling` return
  `available` (consume the leftover). The scrolling body can then never move or dismiss the
  sheet. Dragging the handle or the pinned header still does, which is the expected gesture.
  Only install it when `scrollState.maxValue > 0`, so a short sheet keeps normal swipe-to-dismiss.
- Remove the inner `navigationBarsPadding()` calls; the sheet already applies bottom insets.
  Check the bottom edge in gesture and 3-button navigation.
- Form sheets (category editor, add/edit transaction) pass `dirty`. When dirty,
  `confirmValueChange = { it != SheetValue.Hidden }` and Back / scrim tap show a "Discard changes?"
  dialog. Keep form state hoisted outside the sheet (existing `SheetForm` lesson).
- Quick-Sort: header = remaining count + transaction summary, body = suggestions, "All
  categories", note, remember switch, footer = Ignore / Skip.

**Files.** New `BudgetBottomSheet.kt`; migrate `QuickSortSheet.kt:59-158`,
`CategoryEditorSheet.kt:53-75`, `TransactionSheets.kt:85, 302`, `FilterBar.kt:246, 311`,
`MerchantRulesSheet.kt:51`.

**Acceptance.** Compose UI tests (Robolectric) for the scaffold with a tall body: (1) scroll the
body down, `swipeDown` hard on the body, assert `onDismissRequest` was not called and the header
is still displayed; (2) `swipeDown` on the header dismisses; (3) dirty form + back press shows
the discard dialog and keeps state.

**Device check (emulator, then phone).**
- `adb shell input swipe 540 1800 540 1500 300` then `input swipe 540 1200 540 1700 250` in New
  category: sheet stays, Save visible the whole time.
- Quick-Sort with "All categories" scrolled: `input swipe 540 1300 540 1500 150`: sheet stays.
- Phone: the user repeats the jitter steps; no bounce. Keyboard open on the note field: footer
  and field both visible.

---

## B. Parser improvement (no ML in the app)

Decision kept: rules, not a model. **Do not fork parser-core.** A fork means owning 100+ bank
parsers and 2.2k tests and makes submodule bumps a merge. We need corrections for 7 banks; those
go in our own layer, and each one is also drafted for upstream in `docs/upstream/`. Upstream's
`feat/transfer-loan-direction` branch only touches their app, not parser-core, so nothing to
reuse.

### [x] B1. Golden corpus: extend the existing one (done in 1.0.2 inline in golden/*.txt; chains are TransferChainTest)

**Change to the user's decision:** do not add `ingest/src/test/resources/sms-samples/`. The repo
already has `ingest/src/test/resources/golden/` (7 files, format in its README, `GoldenSmsTest`
runs in CI and fails when a parsed sample breaks). A second corpus would split the truth.

- Add keys to the format and README: `hint` (see B2) and `drop: true` (message must produce
  nothing and store nothing).
- Add `golden/chains/*.txt`: ordered message sequences with expected result after linking:
  per message `link: <name of the other leg>` or `link: -`, plus `expect-spent`, `expect-income`
  totals for the chain.
- Seed from `docs/handoff/probe-sms.tsv` (31 fake messages, expected values in TEST_REPORT
  item 5). Expected values come from reading the SMS, never from the parser.
- Rows marked (?) in the report (SBI debit-by-transfer, SBI NEFT credit) are hand-written guesses.
  Ask the user for real, redacted samples of: an EMI debit, the lender's confirmation, a loan
  account credit, a credit-card bill payment, a self transfer. Replace the guesses.

**Acceptance.** `./gradlew :ingest:testDebugUnitTest --tests '*Golden*'` runs the new cases. New
cases that fail today are checked in with the fix in B2/B3, not marked ignored.

### [x] B2. Rule layer around parser-core

**Goal.** Fix wrong types and merchants and stop storing junk, without editing the submodule.

**Design** (all in `:ingest`, package `ingest.rules`; only `:ingest` sees parser-core):

```
LayeredSmsParser : SmsParser            // replaces PennyWiseSmsParser in IngestModule
  1. PreRule list   -> Drop | Evidence(kind, amountPaise, last4) | Transaction(ParsedSms) | Pass
  2. PennyWiseSmsParser (unchanged)
  3. PostRule list  -> patch the Transaction result (merchant, type, hint)
```

- Rules are Kotlin objects, one file per bank (`HdfcRules.kt`, ...): sender regex + body regex
  with named groups + a builder. No rule files loaded at runtime, no DSL: the rule set stays
  reviewable and R8-friendly.
- `ParsedSms` gains `hint: FlowHint?` with
  `LOAN_REPAYMENT, LOAN_DISBURSAL, CARD_BILL_PAYMENT, SELF_TRANSFER, REFUND, REVERSAL`.
  Stored in a new nullable `flowHint` column on `transactions` (migration 5 -> 6, backup
  version 2).
- `SmsParseResult` gains `Dropped` (store nothing) and `Evidence` (see B3).

**First rules, from TEST_REPORT item 5:**

| Rule | Kind | Effect |
|---|---|---|
| OTP / secret words, any sender | pre, Drop | nothing stored (this is E1) |
| "is due on", "will be debited", "has bounced", "declined", "requested Rs" | pre, Drop | reminders and failures never reach the unparsed list |
| "credited to your ... Loan A/c" (HDFC L4, SBI L8) | pre, Evidence(LOAN_REPAYMENT) | no income row |
| "received ... payment ... towards your Loan" from any business sender (L3) | pre, Evidence(LOAN_REPAYMENT) | no unparsed row |
| "received payment ... credited to your ... Credit Card" (C2) | pre, Evidence(CARD_BILL_PAYMENT) | |
| HDFC "Info: ACH D- <NAME>-<ref>" (L2, B7) | post | merchant = `<NAME>`; type stays INVESTMENT only if the name matches a fund/clearing list, else EXPENSE with hint LOAN_REPAYMENT when the name matches a lender list |
| "LOAN DISBURSEMENT" in a credit (L5) | post | hint LOAN_DISBURSAL |
| "REFUND", "reversed" in a credit (R1, R2) | post | hint REFUND / REVERSAL |
| payee VPA matches a card-payment pattern (`sbicard.`, `cred.club`, ...) (C1) | post | hint CARD_BILL_PAYMENT |

Lender and fund name lists are small `Set<String>` constants with tests; they are hints, the
user can always re-sort.

**What a hint does.** In `SuggestionEngine.kt:58-60` map hints to seed categories and, when
auto-sort is on, pre-select them: LOAN_REPAYMENT -> new seed "Loan EMI", CARD_BILL_PAYMENT -> new
seed "Credit card bill", SELF_TRANSFER -> existing `self_transfer_out`, LOAN_DISBURSAL -> new seed
"Loan received". All of kind `TRANSFER`, which `Spending.kt:19` and `TransactionDao.kt:184`
already exclude from totals. So **no second exclusion mechanism is added**; the existing category
kind does it. (Improvement on "add a TRANSFER/LOAN_REPAYMENT type": the type already exists as a
category kind. If the user would rather see EMIs inside spending, they change that one category's
kind to Expense; say so in the release notes.)

**Acceptance.** Golden cases from B1 pass. Unit tests per rule (match, near-miss). The 2.2k
upstream tests still pass untouched. `scripts/check-module-boundaries.sh` passes.

**Device check.** Inject L1-L9, C1-C2, O1-O4 from `probe-sms.tsv`. Expect: no income row for L4
or L8, L2 shows merchant "BAJAJ FINANCE LTD" with "Loan EMI" suggested first, unparsed list has
no OTP, promo or reminder.

### [x] B3. Transaction linking

**Goal.** One movement of money is counted once. A self transfer, an EMI, a card bill payment or
a reversal never adds to both Spent and Income.

**Data.** On `transactions`: `linkedToId: Long?` (points at the other leg) and
`linkKind: String?` (same migration as B2). New table `link_evidence(id, kind, amountPaise,
accountLast4, senderHash, receivedAt)`: **no SMS body**, rows older than 7 days are deleted by the
reconcile worker.

**Logic.** Pure `TransactionLinker` in `core/data/.../link/`, unit tests first. Called from
`IngestPipeline.store` inside the same DB transaction. Order-independent (either leg may arrive
first; backfill arrives oldest first).

| Tier | Condition | Action |
|---|---|---|
| 1 | same non-null `reference`, same amount, opposite direction, within 3 days | auto-link. Different accounts: SELF_TRANSFER. Same account, credit after debit: REVERSAL |
| 2 | a transaction with hint LOAN_REPAYMENT / CARD_BILL_PAYMENT, or matching `link_evidence` (same amount, within 48 h) | set the hint and suggested category on the debit; consume the evidence |
| 3 | same amount, opposite direction, two different own accounts, within 10 min, no references | no auto-link. Show a card like the existing duplicate hint: "Looks like a transfer between your accounts: Link / Keep separate" |
| - | two or more candidates | never auto-link; show the card |

**Totals.** The linked secondary leg (the credit side of a transfer, both legs of a reversal) is
excluded everywhere pending and sorted amounts are summed: `Spending.kt`, `TransactionDao.kt:184`
and the widget queries. It leaves the inbox count too. The primary leg gets the TRANSFER-kind
category from B2. The edit sheet shows "Linked with <other leg>" and an Unlink action.

**Acceptance.** `golden/chains` tests: EMI chain (L1, L2, L3, L4) gives Spent 0 and Income 0 once
sorted as Loan EMI, and exactly one inbox item; S1+S2 linked, Income unchanged; R2 with its debit
nets to zero; an ambiguous pair is not linked. Linker unit tests include: reverse arrival order,
duplicate SMS delivery, backfill of 90 days, unlink then rescan does not relink (remember the
user's choice). Migration test 5 -> 6. Backup round trip keeps links.

**Device check.** Fresh emulator install, inject the chain files, read Analysis totals and the
inbox count; compare with the `expect-*` values.

### [x] B4. Developer-side improvement loop (replaces the "model")

- `docs/PARSER_IMPROVEMENT.md`: the loop. (1) user exports redacted unparsed messages (section C
  or copy from the unparsed list), (2) `scripts/unparsed-to-golden.py export.jsonl` appends stub
  cases (`type: TODO`) to the right `golden/<bank>.txt`, (3) a Claude Code agent, prompt included
  in the doc, fills expected values by reading each SMS, writes a rule in `ingest/rules/`, runs
  `:ingest:testDebugUnitTest --tests '*Golden*'` and `:parser-core:test`, and drafts
  `docs/upstream/000N-*.md` when the fix belongs upstream, (4) human reviews the diff: every new
  expected value and that no real data is in the fixtures.
- The script: Python standard library only, refuses input lines that still contain a 6+ digit
  run or an `@` (a redaction miss).
- Link from `CONTRIBUTING.md` and `docs/PARSER_UPGRADE.md`.

### B5. "Teach this message" (assessed, deferred; the small version shipped in 1.0.3, ADR-0048)

**Not in 1.0.2; revisit for 1.0.3 or later.**

Effort: large (token-tagging UI, a template compiler, storage, backup, tests for a user-written
grammar). Value now: low to medium. Templates made from one sample are brittle, a wrong tag
causes silent wrong amounts, and B4 fixes the same message for every user. **Do instead (small,
inside task C or after it):** an "Add as transaction" button on each unparsed row that opens the
existing manual-add sheet prefilled with the first rupee amount, a guessed direction, the SMS time
and the body attached as source. Revisit real templates only if unparsed reports keep coming for
banks outside the 7.

---

## C. "Help improve the parser" (opt-in 90-day scan)

**1.0.2 ships C step 1** (owner, 2026-11-15): 90-day in-memory scan, masking with `UnparsedRedactor` (numbers; not the full word allowlist of C2), preview with per-message removal, email to the owner via the mail app (text in the body, so no FileProvider), and Save file. **1.0.3:** the C2 allowlist redactor, attachments if needed, and step 2 of the improvement loop.

**Goal.** From Settings, the user can scan the last 90 days of SMS, see redacted messages that
look financial but did not parse, remove any, and send them by email or save a file. Nothing is
written to budget data, nothing is sent automatically.

### [x] C1. Scanner (no writes, by construction) (1.0.2, ADR-0043)

- `ParserFeedbackScanner` in `:ingest`; constructor takes only `SmsInboxSource`, `SmsParser`
  (the layered one) and `SmsRedactor`. It has **no** DAO, repository, notifier or WorkManager
  dependency, so it cannot write. It calls `parser.parse` directly, never `IngestPipeline.ingest`.
- Keep a message only if: parse result is `Unparsed(NO_MATCH | UNKNOWN_SENDER | PARSER_ERROR)`
  **and** `FinancialSmsHeuristic.looksLikeTransaction` is true (amount + movement word + account
  word, business sender, no secret word). Everything else is dropped in the same loop iteration.
- Result lives in the ViewModel only (not `SavedStateHandle`, not a table). Process death loses
  it; the user rescans. Cap 300 messages; collapse messages whose redacted text is identical into
  one with a count.
- Interface in `:core:data` so `:feature:settings` does not depend on `:ingest`.

Simpler than the "separate table" option and leaves nothing behind to clean up.

### [x] C2. Redactor (allowlist, shape-preserving) (1.0.3, ADR-0046)

`SmsRedactor` in `:ingest` (pure Kotlin, tests first). A blocklist of patterns will miss names;
use an **allowlist**:

1. Drop the message if it matches the secret pattern (OTP, CVV, PIN, password).
2. URLs -> `<URL>`. `x@y` (UPI IDs, emails) -> `<UPI>`.
3. Digits: every digit becomes `9` except the last 2 of an account or card number (digits after
   `a/c`, `acct`, `account`, `card`, `ending`, `XX`, `*`). Separators and lengths are kept, so
   `Rs.1,24,500.00` becomes `Rs.9,99,999.99` and a 12-digit reference stays 12 digits. Amounts
   and balances are masked by default; a toggle "Keep real amounts" (off) leaves amounts only.
4. Words: keep a token only if it is in a built-in vocabulary (banking words, bank names, months,
   weekdays: generated once from the golden corpus plus a reviewed list, about 500 entries, kept
   in a Kotlin file). Any other alphabetic token becomes same-length `x`/`X` preserving case.
   Unknown words, so all names and merchants, never leave the phone.
5. Sender ID is kept. Timestamps are reduced to year-month.

Tests: each rule; a property test that no output contains a 6+ digit run other than 9s, an `@`,
or a token outside the vocabulary; the whole golden corpus round-trips to something that still
has the same amount/keyword positions.

### [x] C3. Preview and share UI (1.0.2 and 1.0.3, ADR-0043, ADR-0047: text in the email body, no FileProvider)

- Settings -> "Help improve the parser". Intro screen: what is scanned, what is removed, that
  nothing is sent until they press Send, link to the privacy text. Button "Scan last 90 days".
- Preview list: sender, redacted text, count, delete per row, "Remove all from <sender>".
  A line at the top: "N messages. This is exactly what will be shared."
- **Save file**: `CreateDocument("application/json")`, same pattern as backup
  (`SettingsScreen.kt:100`). No new component.
- **Email**: `Intent.ACTION_SEND`, type `text/plain`, `EXTRA_EMAIL` = build property
  `budgetwidget.feedbackEmail` in `gradle.properties` (placeholder `<MY_EMAIL>`; hide the button
  when blank), subject, a short body, and the file as `EXTRA_STREAM`. An attachment needs a
  content URI, so add one `androidx.core.content.FileProvider`: `exported=false`,
  `grantUriPermissions=true`, a single `<cache-path name="feedback" path="parser-feedback/"/>`,
  `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData` on the intent. Delete the file on next app start
  and when the preview closes. Wrap in `Intent.createChooser`; catch `ActivityNotFoundException`
  and fall back to Save. No `<queries>` entry, no INTERNET: the mail app sends it.
- File format, one JSON object per line:
  `{"v":1,"app":"<versionName>","parser":"0623c2b","sender":"AD-HDFCBK","reason":"NO_MATCH","count":3,"month":"2026-10","body":"..."}`.
- Update SECURITY_AUDIT section 3 (first provider of ours) and add the provider to a CI check:
  exactly one provider of ours, not exported, only that cache path.

### [x] C4. Docs (PRIVACY.md and the in-app intro; Play listing text comes with D2)

`PRIVACY.md`: new section (opt-in, one-time, runs only when started, what is removed, user
reviews and sends with their own mail app, the app still has no internet permission). Mirror the
text in the in-app intro and the Play listing.

**Acceptance (C as a whole).** Unit: scanner returns only heuristic-positive unparsed messages;
redactor tests. Integration (Robolectric, real Room, fake inbox with 200 mixed messages): after a
scan, row counts of every table are unchanged, no notification posted, no WorkManager work
enqueued, DataStore untouched. Permission check script still passes. Manifest has no new
permission.

**Device check.** Emulator with `probe-sms.tsv` injected plus 5 personal-style messages and an
OTP. Scan: preview shows only the unparsed financial ones, no OTP, no personal message, names and
numbers masked. Delete one. Email: chooser opens, attachment readable in the mail app. Save: file
content matches the preview. `sqlite3` counts before and after are equal.

---

## D. Distribution (2.0.0)

Facts checked on 2026-11-15 (re-check before submitting; items marked *verify* came from
secondary sources):
- New apps and updates must target API 36 since 2026-08-31. The app does (target 36).
- Personal developer accounts created after 2023-11-13 need a closed test with **12 testers opted
  in for 14 continuous days** before production access. Reported in 2026: Google also looks at
  whether testers really used the app (*verify* in Play Console help).
- Android developer verification: enforced since 2026-09-30 in Brazil, Indonesia, Singapore and
  Thailand, global rollout planned for 2027. Unverified apps can still be installed through an
  "advanced flow" with extra friction (a waiting period was reported, *verify*). India is not in
  the first wave.

### [ ] D1. Build and signing

- Add `bundleRelease` to `release.yml`; attach the AAB next to the APK. Run
  `check-permissions.sh` on the universal APK built from the same commit.
- Play App Signing: enrol **with the existing release key** as the app signing key (export with
  the PEPK tool) and create a new upload key. Then Play, GitHub and F-Droid builds share one
  signature and users can move between channels without uninstalling. Store the upload key as
  separate secrets.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }` (block exists at
  `AndroidApplicationConventionPlugin.kt:21`; confirm both flags): F-Droid and IzzyOnDroid reject
  the encrypted dependency blob.
- 16 KB page size: add `zipalign -c -P 16 -v 4 <apk>` to CI (two AndroidX `.so` files ship).
- AGPL source: GitHub's tag archives do **not** include submodules. Attach
  `budget-widget-<version>-source.tar.gz` (repo + `third_party/pennywise` at the pinned commit) to
  every release.

### [ ] D2. Play Console documents (write to `docs/play/`)

1. **`permissions-declaration.md`**, answers for the SMS form:
   - Core functionality: "SMS-based money management". One sentence: "Budget Widget turns bank
     transaction SMS into a list of expenses the user sorts into budget categories; without SMS
     access it has no data source."
   - `RECEIVE_SMS`: show a "which category?" prompt seconds after a payment.
   - `READ_SMS`: import existing bank SMS at setup (user picks 30 or 90 days or none) and catch
     up on messages missed while the app was not running (the app has no boot receiver and no
     background service).
   - Data handling: processed on device only, the app has no INTERNET permission, non-bank
     senders are discarded without storage, OTPs are never stored (after E1).
   - Not the default SMS handler; explain that the exception category applies.
2. **`data-safety.md`**: "Does your app collect or share any of the required user data types?"
   **No.** Reason: nothing leaves the device through the app (no network permission). Encryption
   in transit: not applicable. Deletion: in-app "Start fresh" and uninstall. Note for the user:
   the optional email export is started by the user and sent by their mail app; describe it in
   the privacy policy. If a reviewer disagrees, the fallback answer is "Messages > SMS:
   collected, optional, user-initiated, purpose App functionality, not shared".
3. **`demo-video.md`**, script, 60-90 s, phone screen recording, unlisted YouTube link:
   install -> onboarding SMS disclosure screen (hold 3 s) -> system permission dialog -> Allow ->
   import choice -> a bank SMS arrives (use a test SMS) -> notification with category buttons ->
   tap -> widget -> Analysis -> App info > Permissions showing no network access -> Settings >
   Unparsed list and Start fresh. Voice-over or captions state: on-device only.
4. **`store-checklist.md`**: Finance category; content rating; target audience 18+; ads: none;
   app access: no login; financial features declaration ("personal budgeting, no regulated
   service"); privacy policy URL; listing text from `fastlane/metadata/android/en-US/` with the
   SMS use stated in the first paragraph of the full description; no PennyWise branding; source
   link and AGPL note; feature graphic 1024x500, icon 512, at least 4 phone screenshots with fake
   data.

### [ ] D3. In-app disclosure wording

Onboarding step 2 already shows a disclosure before the system prompt and needs an explicit tap.
Add what Play's policy text asks for: that bank SMS are read **also when the app is closed or
not in use** (the receiver runs in the background), and name the data ("sender and text of bank,
card and UPI messages"). Keep "Not now" working: the app must stay usable with manual entry.
File: `feature/onboarding/.../OnboardingScreen.kt` + strings. Screenshot test updated.

### [ ] D4. Privacy policy URL

Publish `PRIVACY.md` as a normal web page (GitHub Pages from `docs/`, or
`sohankuna.com/budget-widget/privacy`). It must be public, not a PDF, name the app and the
developer, give a contact email, and cover SMS handling, storage, retention, deletion and the
opt-in export (C4). Use the same URL in Play Console, the listing and the About screen.

### [ ] D5. Closed test plan

Create the closed track as soon as D1 works. Recruit 15+ testers (margin over 12): friends and
family with Indian bank SMS, plus r/AndroidClosedTesting style communities. Ask each to use it
for the 14 days and send feedback once; keep notes, the production-access form asks what was
learned. Do not pause the track during the 14 days.

**Play verdict: risky but realistic.** The use case is a named exception, the disclosure and the
no-network proof are strong. The biggest blocker is not the SMS review but the **12 testers for
14 days** gate, followed by a possible SMS-form rejection that needs one or two rounds.

### [ ] D6. F-Droid / IzzyOnDroid (parallel channel)

- IzzyOnDroid first: it takes the signed APK from GitHub Releases. Requirements: FOSS licence
  (AGPL ok), no proprietary libraries (none), APK under 30 MB (3.7 MB), fastlane metadata in the
  repo (present). Open the inclusion request after D1.
- F-Droid main repo: builds from source. Needs: recipe with `submodules: true`, a toolchain the
  build server has (AGP 9.5 / Gradle 9.9 / JDK 21 / compileSdk 37: check before applying), and
  ideally a **reproducible build** so F-Droid ships the developer-signed APK. Add
  `docs/REPRODUCIBLE_BUILD.md` and a CI job that builds the tag twice in clean containers and
  diffs with `apksigcopier compare`. Known sources of drift to pin: baseline profile ordering,
  build path, timestamps, the R8 map id.
- Anti-features: none expected.

### [ ] D7. Sideload experience (`README.md` install section)

- Keep the existing Play Protect steps. Add: installing through a store-style installer
  (F-Droid, IzzyOnDroid, Obtainium) or `adb install` avoids most browser-download blocks, and
  updates then come automatically.
- Once the app is on Play with the same signing key, Play Protect knows the certificate, which
  should reduce the "unknown app" warning (*verify* after publishing).
- Register the package and signing key for Android developer verification (comes with the Play
  Console account) before the 2027 global rollout, so sideloaded copies keep installing without
  the advanced flow.
- Publish the release certificate SHA-256 in the README so anyone can check an APK with
  `apksigner verify --print-certs`.

### AGPL obligations when distributing

- Offer the complete corresponding source for each binary: tag = build, plus the source tarball
  with the submodule (D1).
- Keep LICENSE and NOTICE in the repo and reachable in the app (About screen has them), credit
  PennyWise, state that parser-core is unmodified at commit `0623c2b`.
- Put the source link in the store listing. No extra terms on top of the AGPL.
- Section 13 (network use) is not triggered: there is no network service.
- Signing keys do not have to be shared: users can build and install their own copy.

---

## E. Security fixes

IDs in brackets are from SECURITY_AUDIT.md.

### [x] E1. Never store OTP or non-financial bodies [F9]

- Files: `core/data/.../ingest/IngestPipeline.kt:96-131`, `ingest/.../parser/FinancialSmsHeuristic.kt`,
  `SmsParser` interface.
- Approach: split the heuristic into `containsSecret(body)` and `looksLikeTransaction(sms)`.
  In the pipeline: if `containsSecret`, return `NotFinancial` for every sender before parsing.
  For a known sender with `Unparsed(NO_MATCH)`, store the body only when the amount + movement +
  account test passes; otherwise return `NotFinancial`. (B2's pre-rules later take over the
  secret check; keep this guard anyway.)
- Migration 5 -> 6 (or a one-off cleanup at startup): delete `unparsed_sms` rows whose body
  fails the same test.
- Update `PRIVACY.md` ("OTPs are never stored").
- Acceptance: unit tests on the pipeline for OTP, promo, reminder, balance alert from a known
  sender (nothing stored) and a real unknown format (stored). Migration test.
- Device check: inject O1 and O4 from `probe-sms.tsv`; `select count(*) from unparsed_sms` does
  not change.

### [x] E2. Screen privacy [F1]

`MainActivity` and `QuickSortActivity`: `setRecentsScreenshotEnabled(false)` on API 33+ by
default. New setting "Block screenshots" (default off) applies `FLAG_SECURE` to both. Check:
recents shows a blank card; with the toggle on, `adb exec-out screencap` returns black.

### [x] E3. Log hygiene [F7]

`core/data/.../inbox/InboxEvents.kt:28`: log the listener class and the exception class name
only. Add a lint or grep check in CI: no `Log.` call outside an allowlist of files.

### [x] E4. "Does not try" dex check [F8]

Extend `scripts/check-permissions.sh` (or a new `check-no-network-code.sh`): fail when the release
dex references `Ljava/net/Socket`, `Ljava/net/HttpURLConnection`, `Ljava/net/URL;`, `Ljavax/net/`,
`Landroid/webkit/` or `Ldalvik/system/*ClassLoader` (today: zero hits). Run in `ci.yml` and
`release.yml`.

### [x] E5. Password-protected backup [F2] (1.0.3, ADR-0044)

**1.0.2:** only a warning dialog before CSV export and backup (plaintext, choose a private folder).
Encryption itself moved to **1.0.3**.


Optional passphrase in the Back up dialog. PBKDF2-HMAC-SHA256 (600k iterations, 16-byte salt) ->
AES-256-GCM, `javax.crypto` only. File = small JSON header (`format`, `kdf`, `iterations`, `salt`,
`nonce`) + ciphertext. Restore detects the header and asks for the passphrase; wrong passphrase
is a clean error. CSV export stays plaintext with a warning line. Tests: round trip, wrong
passphrase, truncated file, old plaintext backup still restores.

### [x] E6. SMS body retention [F3] (1.0.3, ADR-0045)

Setting "Keep original SMS text": Forever (default) / 90 days / 30 days. The reconcile worker
nulls `smsBody` on sorted transactions older than the limit. No SQLCipher (reasoning in
SECURITY_AUDIT section 4).

### [x] E7. Supply chain [F4, F5] (1.0.3, ADR-0050)

`gradle/verification-metadata.xml` with sha256 for all configurations, refresh steps in
`CONTRIBUTING.md`. `actions/attest-build-provenance` on the release job (`id-token: write`,
`attestations: write`). Reproducible build from D6.

### E8. Phone build [F6] (no action)

Checked on 2026-11-15: the phone already runs the signed GitHub release, not a debug build.
Nothing to do; follow "Updating the phone" above.

---

## Kickoff prompt for Opus

```
Implement docs/handoff/PLAN.md in the budget-widget repo. Read CLAUDE.md, docs/SCOPE.md and all
three files in docs/handoff/ first. Work the "Order" table top to bottom: E1, A1, A2, B1, B2, B3,
E2-E4, C, B4, E5-E6, D, E7. One task = one branch m9-<id>-<slug> from main = one draft PR; never
merge. For each task: reproduce or write the failing test first, implement, then run
`TZ=UTC scripts/capped.sh ./gradlew spotlessCheck lint test assembleDebug assembleRelease` and the
scripts/check-*.sh, install on emulator bw36 and do the task's "Device check" exactly as written
(stop the emulator before full Gradle runs). Commit small with Conventional Commits, no co-author
lines, no em dashes. Tick the task's checkbox in PLAN.md in the same PR and add an ADR for
behaviour changes. A2 step 0 and all "phone" checks need the user with the phone unlocked: ask,
do not guess. Never edit third_party/, never add a permission, never store an SMS body the plan
does not allow. Compact between tasks; PLAN.md is the source of truth for what is done.
```
