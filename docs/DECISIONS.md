# Decisions

Short ADR-style records. SCOPE.md is the spec; these cover what it leaves open, and any
deliberate deviation. Newest at the bottom. Status is Accepted unless stated.

## ADR-0001: Pin PennyWise parser-core at `0623c2b`
- **Context:** SCOPE.md §2 verified our 7 banks against upstream `main` @ `0623c2b` (2026-11-08). On 2026-11-09 upstream HEAD was `17eab5e`, with no changes under `parser-core/` since `0623c2b`.
- **Decision:** pin the submodule at `0623c2b94c2d61c2a30a9068c5525bde83f6a46c`, the reviewed commit. Pin `kotlinx-datetime` to `0.7.1` to match upstream, even though 0.8.0 exists; it is bumped only together with the submodule (Dependabot ignores it).
- **Consequence:** upgrades follow [PARSER_UPGRADE.md](PARSER_UPGRADE.md).

## ADR-0002: Run the full upstream test suite from upstream's directory
- **Context:** `SupportedBanksDocTest` and `BankSamplesDocTest` walk up from the working directory to upstream's `settings.gradle.kts` and read upstream docs. `IosCompatibilityTest` reads `src/main/kotlin` relative to the working directory.
- **Decision:** `:parser-core:test` uses `workingDir = third_party/pennywise/parser-core` and runs the whole upstream suite, with no exclusions. At `0623c2b` that is 164 classes and 2196 tests, with 1 env-gated skip. Two guards back this up:
  - `verifyOurBankTestsRan` fails unless each of our 10 bank test classes ran ≥1 test with no failures.
  - `verifySubmoduleClean` fails if testing modified the submodule.
- **Decision:** use JUnit Jupiter 6.1.3, the latest stable, instead of upstream's 5.10.0. The upstream tests pass unchanged on it. If a future upstream test needs 5.x, pin 5.x here and note it.

## ADR-0003: Package root and applicationId `io.github.swashbuckler104.budgetwidget`
- **Context:** the applicationId is permanent once installed: changing it means a new app, and data only moves via backup and restore. The prompt's placeholder `io.github.budgetwidget` sits under a GitHub namespace we don't own.
- **Decision:** use `io.github.swashbuckler104.budgetwidget`, defined only as `budgetwidget.packageRoot` in `gradle.properties`. Module namespaces are derived from it (`<root>.core.model`, ...). The Kotlin package directories must match if it is ever renamed before v1.0.0.

## ADR-0004: Build on JDK 21, target Java 17
- **Decision:** Gradle runs on JDK 21 (upstream's toolchain). All bytecode targets Java 17. JVM modules (`:core:model`, `:parser-core`) compile with `-Xjdk-release=17`, so JDK 18+ APIs can't leak into code that runs on Android.

## ADR-0005: Version catalog grows per milestone
- **Decision:** `gradle/libs.versions.toml` only lists what the build uses today. Each milestone adds what it needs, using the latest stable version at that time. This avoids unused, silently stale entries.

## ADR-0006: Lint warnings are errors; version-drift checks are informational
- **Decision:** `warningsAsErrors` and `abortOnError` in every module, with no baselines. `GradleDependency`, `NewerVersionAvailable` and `AndroidGradlePluginVersion` are informational so that a third-party release can't turn CI red; Dependabot proposes upgrades instead. `OldTargetApi` is informational because targetSdk is pinned (ADR-0007).

## ADR-0007: compileSdk 37, targetSdk 36 (deviation from the prompt)
- **Context:** the agent prompt said "targetSdk/compileSdk 36" and also "latest stable versions of all libraries". Current stable AndroidX (Compose 1.12 via BOM 2026.09.00, core 1.19) refuses to build against compileSdk 36. SCOPE.md §2 only locks minSdk 26 and **targetSdk** 36.
- **Decision:** compileSdk 37, targetSdk 36. compileSdk only selects the API stubs we compile against. It changes no runtime behaviour, and targetSdk 36 keeps the Android 16 behaviour the spec chose. Code that uses an API above minSdk still needs `SDK_INT` checks, as lint enforces.
- **Revisit:** when moving targetSdk to 37 (a spec change).

## ADR-0008: No Android cloud backup or device transfer
- **Decision:** `allowBackup="false"`, plus `dataExtractionRules` (Android 12+) and `fullBackupContent` (Android 11 and lower) that exclude every domain. SCOPE.md §1.4 says all data stays on the phone, and Android Auto Backup would upload the database to Google Drive even without the INTERNET permission. Users move data with the in-app JSON backup (F9).

## ADR-0009: setup-gradle with the open-source cache provider
- **Decision:** CI uses `gradle/actions/setup-gradle` with `cache-provider: basic`, the MIT-licensed provider on `actions/cache`, rather than the default proprietary "enhanced" provider. Caches are written only from `main`. Release builds are read-only and don't use the configuration cache, so signing secrets never reach a cache entry.

## ADR-0010: Enforce "no INTERNET" on the built APKs, not with a manifest stub
- **Context:** a `tools:node="remove"` entry with nothing to remove triggers a manifest-merger warning.
- **Decision:** `scripts/check-permissions.sh` checks the debug and release APKs in CI against an **allowlist** (`config/permissions-allowlist.txt`), with a dedicated INTERNET failure message. This also catches any other permission a dependency merges in. Add `tools:node="remove"` only once a dependency actually brings INTERNET in.

## ADR-0011: Constrain androidx.lifecycle to the latest stable
- **Context:** Compose and activity pull lifecycle 2.9.x in transitively. Its bundled lint checks reference APIs that AGP 9.5 lint lacks (`ObsoleteLintCustomCheck`, an error under ADR-0006).
- **Decision:** the Android convention adds a dependency constraint on `androidx.lifecycle:lifecycle-runtime` 2.11.0. Lifecycle artifacts align as a family, so this lifts them all.

## ADR-0012: Release signing only from the environment
- **Decision:** `:app` signs release builds only when `KEYSTORE_PATH` is set, and then requires `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. In CI these come from secrets (`release.yml`). Without them, `assembleRelease` produces an unsigned APK, so anyone can build. Keystores are gitignored.

## ADR-0013: WorkManager permissions (no boot receiver, no network state)
- **Context:** WorkManager merges `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` and `FOREGROUND_SERVICE`. SCOPE.md forbids the first.
- **Decision:** the app manifest removes `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE` (only the app's merge sees WorkManager's entries) (we never use network constraints). It keeps `WAKE_LOCK`, which WorkManager uses while workers run, and `FOREGROUND_SERVICE`, which expedited work needs on API 26-30.
- **Consequence:** after a reboot, scheduled work comes back on the next process start. An incoming SMS starts the process, and so does opening the app. `BudgetWidgetApp.onCreate` re-arms the periodic scan and runs a reconcile, which catches up from the checkpoint. Nothing is lost; it may arrive later.

## ADR-0014: `SmsParser` and `ParsedSms` live in `:core:model`
- **Context:** SCOPE.md §5 puts the pipeline in `:core:data` and the parser adapter in `:ingest`, and `:core:data` must not depend on `:ingest`.
- **Decision:** the interface and our result types (`RawSms`, `SmsParser`, `SmsParseResult`, `ParsedSms`) are plain Kotlin in `:core:model`. `:ingest` implements them with PennyWise (`PennyWiseSmsParser`, `ParsedTransactionMapper`) and binds them with Hilt. `:ingest` remains the only module that sees `com.pennywiseai.*`.

## ADR-0015: Ingestion details the spec leaves open
- **Only bank senders reach WorkManager.** `SmsReceiver` checks `isKnownBankSender` before enqueuing, because WorkManager persists input data. Personal SMS never touch disk (hard constraint 4).
- **Parser exceptions become `PARSER_ERROR` unparsed entries.** Otherwise one odd SMS would fail the reconcile scan on every run and stall the checkpoint.
- **First scan without a checkpoint looks back 1 day.** Older history is imported only through an explicit backfill, whose rows are flagged `isBackfill`.
- **A merchant that is the user's own account or card is dropped** ("A/c XX8812", "your ... Card ending 6788"). Several upstream formats return one (docs/upstream/0003). Showing "Unknown merchant" is better than training merchant rules on an account number.
- **The checkpoint never moves past "now",** so an SMS dated in the future can't make later scans skip messages.
- **The table is named `transactions`,** because `TRANSACTION` is an SQL keyword. All other SCOPE.md §5 tables keep their names and exist from schema version 1.

## ADR-0016: Test stack for Android modules
- **Decision:** our Android modules use JUnit 4, Truth and Robolectric 4.17, which runs only on JUnit 4. Room, DataStore and the pipeline are tested against real in-memory implementations rather than mocks. `:parser-core` keeps upstream's JUnit Jupiter (ADR-0002).
- **Robolectric on SDK 36** needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`, set in the library convention.
- **Gradle's "no tests discovered" guard** stays on for every module that has a `src/test` directory. It is off only where Hilt generates test sources but no tests exist yet.

## ADR-0017: Narrow upstream's bank-sender detection
- **Context:** upstream parser-core covers banks in many countries, and some match senders loosely. `EverestBankParser` (Nepal) claims every 7-10 digit sender, which is how many Indian phones show a contact's number. During M1 end-to-end testing, a personal SMS from a numeric sender landed in the unparsed list with its body. That violates hard constraint 4.
- **Decision:** `PennyWiseSmsParser` treats a sender as a bank only if it is not a phone number (`+` and digits, ignoring spaces, dashes and parentheses), not a TRAI promotional header (`XX-HEADER-P`), and at least one *INR* parser claims it. On a real phone, 11 of 113 imported "transactions" in 30 days were Kotak card offers from `KOTAKB-P` (docs/upstream/0005). Parsing uses only those parsers, in upstream's order. Indian banks must send from registered alphanumeric headers, so no real bank SMS is lost. Foreign-currency bank SMS are now ignored rather than logged as "unsupported currency": v1 is INR-only, and not storing them is the privacy-preserving choice.
- **Upstream:** reported as a draft in `docs/upstream/0004-everest-numeric-senders.md`. Pinned by `PennyWiseSmsParserTest` and a `NOT_BANK` golden case.

## ADR-0018: Categories, suggestions and auto-sort (M2)
- **Seed set:** the owner's list (SCOPE.md "Seed categories"). It replaces the original. Seeds are inserted once; the inserted version is stored in settings, so seeds the user deletes or renames are never re-created.
- **Self transfers have two legs:** "Self Transfer" exists under Financial Expenses (outgoing) and under Income (incoming), so the two SMS of a transfer between your own accounts cancel out. Suggestions pick the right leg by direction.
- **Investments are a transfer kind:** money moved to your own investments isn't spending, and money can come back from them.
- **Stable seed keys:** `category.seedKey` (schema v2, Room auto-migration with `MigrationTest`) identifies seeds across renames. Type hints and default suggestions use it.
- **Icons are one emoji,** limited to Emoji 5.0 in the seeds and the editor grid. That keeps them renderable on Android 8 (minSdk) in widgets and notifications, which can't use EmojiCompat, and needs no icon assets.
- **Suggestion order** (SCOPE.md F8), with one addition:
  - the merchant's rule;
  - the merchant's history;
  - **the transaction type's hint** (SCOPE.md F2 table: transfer, investment);
  - the 60-day ±50% amount band;
  - global usage;
  - seed defaults;
  - then remaining leaves in display order.
  Only active leaves whose kind fits the direction are suggested. The full picker shows every active leaf, so any choice stays possible.
- **Sorting rules:** a transaction can only point at an active leaf. With "Remember" on (the default), the merchant rule learns: same category increments `confirmCount`, a different one resets it to 1.
- **Auto-sort** (pipeline step 6, setting off by default) applies rules confirmed 3+ times, never to a possible cross-bank duplicate. The toggle UI comes with Settings (M8).
- **Delete vs archive:** a category is hard-deleted only when no transaction uses it or its subcategories. Otherwise the UI offers archive. Archiving a category archives its subcategories.
- **Reorder:** drag handles for categories, plus "Move up/down" in the menu and as TalkBack actions, for subcategories and accessibility.
- **Quick-Sort** is a transparent `QuickSortActivity` (own task, excluded from recents) hosting a `ModalBottomSheet`. Opened with a transaction id it sorts that one and closes; without one it walks the inbox oldest first, and Skip moves an item to the back.

## ADR-0019: Transaction notifications (M3)
- **Events, not polling:** `SortTransactionUseCase` and `IngestPipeline` report to a Hilt set of `InboxListener`s after their write commits. `:notifications` posts on "pending added" and cancels on "sorted", whatever did the sorting: sheet, widget or a notification button. A failing listener is logged and skipped, so a notification problem can never block storing or sorting.
- **Who gets a notification:** SMS from the real-time receiver; SMS stored by the inbox scan only when they are at most 24 h old (the receiver missed them, but older ones are old news); never backfill; never auto-sorted ones. Then the settings: on/off and "only for amounts of at least ₹X" (default 0 = every amount; "at least" so ₹X itself notifies).
- **Buttons:** the top 3 suggestions, padded with Ignore (SCOPE.md F3). A possible cross-bank duplicate leads with **Same payment**, which ignores it with reason `DUPLICATE` (the spec's Merge), and its text says which payment it may repeat. Buttons are broadcasts to a non-exported receiver that uses `goAsync` and a Hilt entry point, so they work with the process cold.
- **Stale buttons:** notification and widget actions pass `requirePending`, so a button on a transaction already sorted elsewhere does nothing. If the button's category was archived or deleted since, the notification is re-posted silently with fresh buttons.
- **Title** `-₹450 · Swiggy`: the sign is spelled out next to the red/green accent, because color must never be the only signal (SCOPE.md §6). The lock screen gets a public version without amount, merchant or account.
- **Stays until sorted** (`autoCancel = false`): tapping opens the Quick-Sort sheet; if the user backs out unsorted, the prompt is still there.
- **Grouping:** a silent summary (`GROUP_ALERT_CHILDREN`) appears at 2+ notifications and stays until the last child goes: cancelling a group summary makes Android cancel all its children too (found on the emulator: the last pending notification vanished), and with one child the system shows that child alone. The notifier keeps the set of posted notifications in memory under its lock (seeded from the system once per process), because the system list is updated asynchronously and can lag several posts in a burst. Swipes come back through each notification's delete intent. The status is re-checked under the lock right before posting, so a sort landing meanwhile can't leave an orphan.
- **Permission:** `POST_NOTIFICATIONS` (allowlisted). Settings shows a "blocked" card with Allow, which asks once and opens system settings only after "don't ask again". The onboarding prompt comes in M8.
- **Settings** gets a screen now (gear on Analysis and Transactions). It holds notifications and the auto-sort toggle from ADR-0018; M8 fills in the rest of SCOPE.md F9.

## ADR-0020: Quick-Sort widget and closing the day (M4)
- **Widget = Glance, four responsive breakpoints:**
  - 2x2 (110 dp): the count, or "Sorted ✓" with the streak.
  - 4x2 short (250x110 dp): 4 chips plus Ignore and More, arrows in the header. Many launchers give a 4x2 widget only about 110 dp of height.
  - 4x2 standard (250x180 dp): 6 chips plus the `‹ Ignore More ›` footer.
  - 4x3+ (250x240 dp): standard plus the next two.
  - Amounts are signed. Chips have a TalkBack label ("Sort as …"). The arrows are auto-mirrored for RTL.
- **Per-instance position:** each widget instance stores its current transaction **id** (not an index) in its Glance state, so a new SMS doesn't shift what you're looking at. Sorting moves on to the item that took its place. Chip, Ignore and arrow actions are Glance `ActionCallback`s, and the chip and Ignore sorts use `requirePending`.
- **Refreshing:**
  - In-process, a debounced (500 ms) observer calls `updateAll` after any write to transactions, categories or recorded days. It watches cheap change signals (a `COUNT(*)` query that Room re-runs on each invalidation), never the rows. Every write happens in our process, so nothing is missed.
  - Entry points that may die right after (ingest and reconcile workers, notification buttons, day alarms) call `InboxWidgets.refreshNow()` before returning.
  - `refreshNow()` also bumps a process-wide signal the snapshot flow listens to: `updateAll` only recomposes a widget whose Glance session is alive, which would otherwise keep time-based text (countdown, Overdue, today) stale after a tick or clock change.
  - `updatePeriodMillis = 0`: no periodic wakeups.
- **Day alarms:** inexact `AlarmManager.setWindow` (10 min window; SCOPE.md allows it, no exact-alarm permission).
  - Ticks at 00:05, 21:00, 22:00 and 23:00 (non-wakeup) record finished days and refresh the countdown (`~3/2/1 h left`).
  - A separate wakeup alarm sends the nudge.
  - Each alarm re-arms itself. Every process start and every nudge-setting change re-arm both (they're lost on reboot; we have no boot receiver, ADR-0013). So do clock and time-zone changes and app updates (`TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` to a non-exported receiver).
  - A nudge alarm posts only if it fires on its own day within 1 h of its time. A later delivery (Doze, or a 23:55 nudge landing after midnight) is skipped, because "left to sort today" would be wrong, and the next day's nudge stays armed. The last nudge day is stored, so re-arming never nudges twice.
  - The armed nudge time is stored. Re-arming (process start, alarms, clock changes) keeps that exact nudge if it is due and hasn't run, so a restart while its alarm was pending doesn't lose it; otherwise the next time still ahead is armed. A time the user picks only counts if it is still ahead, and since only an armed alarm can be resumed, a later restart can't bring back a time moved into the past.
  - A nudge still up from an earlier day is cleared at the next tick.
  - Every process start redraws the widgets: re-arming can replace a non-wakeup tick that was still waiting while the screen was off.
- **Closing a day is computed, not sampled at midnight:** schema v3 adds `transactions.sortedAt` (when a row first left the inbox; the migration fills it from `updatedAt`).
  - A day closed if no non-backfill transaction was created before its local midnight and still unsorted then.
  - Days are recorded once, after they end, whenever any alarm, the app start or the widget runs. A late or missed midnight gives the same answer.
  - Tracking starts the first time this runs (no judging the past). Catch-up is capped at 62 days.
- **Streak:** consecutive closed days ending yesterday, **plus today once the inbox is empty**, so sorting the last item shows the reward at once. A new transaction takes today back out.
- **"Today ₹X" (spent):** outgoing, not ignored, and not sorted into a transfer-kind category (self transfers and investments aren't spending). Pending ones count. M6 reuses this definition.
- **Overdue vs closing:** "Overdue" follows SCOPE.md §3 (`occurredAt`, the SMS time); closing a day uses `createdAt` (when we stored it). An SMS stamped 23:59:50 but delivered after midnight shows as overdue yet doesn't count against the day before, which is the lenient choice for the streak.
- **Streak display:** on the widget's empty state and in the Analysis header (SCOPE.md F6).
- **Nudge:** stays until the inbox is clear (like transaction notifications, it stands for work still waiting) and its count follows each sort. "N transactions left to sort today" (non-backfill pending) with "Sort now" and the streak at stake. Its own channel "Evening reminder" (default importance). Cancelled when the inbox is cleared. On at 21:00 by default; Settings has the switch and a time picker.

## ADR-0021: Transactions tab (M5)
- **Inbox pinned, then days:** pending transactions are a section at the top (oldest first, 5 shown with "Show all"), so they aren't repeated in the day list. Tapping one opens the Quick-Sort sheet for it; a possible duplicate shows "Looks like the same payment as ₹X at 8:40 PM" with **Merge** (ignored with reason `DUPLICATE`, never counted) and **Keep both** (the flag is cleared). Any search or filter switches to one result list grouped by day, pending ones included.
- **Day totals** use the ADR-0020 spending definition: outgoing, not ignored, not in a transfer-kind category (pending counts); income is its mirror.
- **Search** matches merchant, note, category and bank name (ignoring case), and amounts by prefix as typed ("45" finds ₹450 and ₹4,500; "₹1,200.5" finds ₹1,200.50).
- **Filters** are single-choice chips: status (to sort, sorted, auto-sorted, ignored; "auto-sorted" is SCOPE.md F8's "Review auto-sorted"), type (money out/in), category (a top-level one includes its subcategories), account and a date range. Filtering runs in memory over a light list projection (no SMS body, raw merchant or reference): a family phone holds a few thousand rows, and it keeps search instant without a query per keystroke.
- **Edit sheet:**
  - The type toggle (Expense / Income / Transfer) sets the direction; a transfer keeps money out/in. The picker shows only categories of that type, and a switch that invalidates the current category reopens it. Kind and direction are also checked when saving.
  - Saving sorts through `SortTransactionUseCase`, so notifications and widgets follow. A changed category teaches the merchant rule unless "Remember" is off.
  - **Renaming a merchant** with "Rename every transaction from this merchant" renames past transactions and stores `merchant_alias(merchantKey, displayName)` (schema v4), which the pipeline applies to new SMS. Naming an unknown merchant gives the transaction a merchant key, so its rule can learn.
  - SMS amounts and times are read-only. "Report wrong amount" files the SMS in the unparsed list (reason `WRONG_AMOUNT`) for a parser bug report. The original SMS is shown on request and is selectable.
- **Manual (cash) transactions** are stored already sorted (`sortedAt = createdAt`), so they never count against closing a day. They have no account ("Cash"), and they are the only ones that can be deleted; SMS ones are ignored instead.
- **Bulk and swipe:** long-press starts a selection (bulk categorize or ignore). Swiping right opens the picker; swiping left ignores, or brings an ignored one back to the inbox. Both swipe actions are also TalkBack custom actions. Bulk sorts don't teach merchant rules: one choice for a mixed selection shouldn't become every merchant's rule.
- **Undo:** every destructive action (sort, ignore, merge, keep both, back to inbox, delete) snapshots the affected rows, plus the merchant rules a sort taught, and the snackbar's Undo writes them back as they were, including `sortedAt`, so a day close isn't skewed.
- **The M1 debug screen is gone.** "Check SMS now" and "Import last 30 days" moved to the overflow menu until onboarding and settings take them over (M8).
- **Design:** a brand palette (teal, seed #006B5E) for devices without dynamic color, fixed money colors (income green, spending red), tuned for light and dark and derived from the active scheme. Signs are always written out. List amounts stay neutral, with red only on totals and the inbox, so a long list isn't a wall of red. Amounts use tabular figures.

## ADR-0022: Analysis tab (M6)
- **One calculation, `MonthAnalysis.compute`** (pure, in `:core:data`, unit-tested), over the same list rows the Transactions tab observes. It covers totals, categories, daily and cumulative series and top merchants. The Insight widget (M7) reuses it, so the app and the widget can't disagree.
- **Definitions** (ADR-0020's spending rule throughout):
  - **Spent:** outgoing, not ignored, not transfer-kind; pending counts.
  - **Income:** the incoming mirror.
  - **Net** = income - spent.
  - **Per day** = spent / days elapsed (whole month for past months).
  - **Comparison:** the current month is compared with the previous month **up to the same day of month** (a fair "so far"); a past month is compared with the whole month before.
- **Categories:** the donut groups spending by top-level category, plus a grey **"To sort"** slice for spending not sorted yet, so the total always adds up. Tapping a slice or its row shows the subcategory breakdown and "See N transactions". A subcategory row or a top merchant opens the same list too.
- **Drill-down = the Transactions screen** with a fixed filter (category or merchant search, month range, account), in the Analysis tab's back stack (`DrillDownDestination`). There is no second list implementation.
- **Charts are custom Canvas composables** in `:core:designsystem` (donut, bars with a dashed average line and today highlighted, cumulative lines, proportion bars), not Vico. That adds no dependency for four simple charts, matches the theme exactly, and every chart has a TalkBack summary (largest values, average, running totals).
- **Month switcher:** arrows plus horizontal swipe, bounded by the oldest transaction's month and the current month. The account filter appears when there are 2+ accounts.
- Averages and the three small cards show whole rupees (`formatInrRounded`). The hero total keeps paise.

## ADR-0023: Insight widget and widget styling (M7)
- **Content (SCOPE.md F5):**
  - Today's spending.
  - This month so far with ▲/▼ against last month up to the same day (green when less, red when more, read out by TalkBack).
  - A 7-day bar chart with today highlighted and weekday initials.
  - From about 4x3, the month's top categories (or "To sort") with proportional bars in each category's color.
  The numbers come from `MonthAnalysis` (ADR-0022), so the widget and the Analysis tab agree.
- **The chart is a bitmap, rendered as alpha masks** (`ALPHA_8`) that Glance tints with theme color providers: one mask for the other days (secondary), one for today (primary). SCOPE.md asks for bitmaps with dynamic colors resolved at render time; tinting masks instead means light/dark and wallpaper-color changes apply instantly, without a re-render. A mask is a quarter of an ARGB bitmap in the RemoteViews parcel. The category bars are plain Glance boxes sized from the exact widget width, because no custom drawing is needed for a bar.
- **No separate InsightRenderWorker:** rendering happens in `provideGlance`, which Glance already runs in its own WorkManager session worker. Refresh paths:
  - in-process data changes redraw idle widgets 2 s after the last change (Quick-Sort keeps 500 ms);
  - a live session's snapshot flow is debounced 2 s too;
  - `InboxWidgets.refreshNow()` (workers, notification buttons, day alarms, including the 00:05 tick) redraws both widgets, so "today" rolls over at midnight.
- **Size mode Exact** so the bitmap matches the real size. The chart keeps at least 56 dp when categories show, and only as many category rows as fit are shown. Resizable 4x2 to 4x4 (`maxResizeHeight` 400 dp), with a static picker preview on API 31+.
- **Tap opens Analysis** through a navigation deep link (`budgetwidget://analysis`) on the app's launch intent (new task). `:widget` doesn't depend on `:app`, and no exported intent filter is added.
- **Widget colors (both widgets):**
  - Dynamic color on Android 12+ (as before); the app's brand palette below, via `glance-material3` `ColorProviders`, instead of Glance's default purple.
  - Quick-Sort is restyled with a hierarchy: the transaction sits on a surface card, the amount is signed and green/red (the money colors, as in the app and notifications), the count is in the primary color, chips use the tonal secondary container, Ignore is quiet and **More** is the filled primary action.

## ADR-0024: Fixed daily categories on payment notifications
- **Context:** on the phone, the owner found the suggested buttons on transaction notifications not very useful. Their daily spending is almost always groceries, restaurants or public transport, and anything else is better sorted from the Quick-Sort widget (6 chips plus More) or the sheet.
- **Decision (owner's call, 2026-11-10; replaces SCOPE.md F3's "top 3 suggested categories" for payments):**
  - **Payment** notifications always offer **Groceries, Restaurant/Fast-food, Public transport**, in that order. They are found by seed key (`groceries`, `restaurants`, `public_transport`), so renaming them keeps the buttons.
  - An archived or deleted one, or one that gained subcategories, is skipped, and the row is padded with Ignore.
  - A possible duplicate still leads with **Same payment** (then the first two).
  - **Incoming** money keeps the suggestion engine (income categories), since expense shortcuts can't hold it.
  - The Quick-Sort widget and sheet keep the full suggestions.
- Pinned by `TransactionNotifierTest` ("payments always offer the daily categories").

## ADR-0025: Follow-ups from the M5-M7 review
- **A sort must fit the money's direction** (`SortResult.KindMismatch`, checked in `SortTransactionUseCase`, so it covers the sheet, widget, notifications and bulk). A mixed bulk selection sorts what fits and reports "N skipped". A notification button that no longer fits re-posts with fresh buttons.
- **Edit sheet saves atomically:** details, merchant rename and sort happen in one transaction (a failed sort rolls everything back), and the "sorted" event fires after the commit. Saving an auto-sorted transaction with its category **confirms** it: it leaves "Review auto-sorted" and the rule counts the confirmation (SCOPE.md F8).
- **Undo never overwrites a later change:** each undo records every row's `updatedAt` right after the action and restores only rows still at that stamp. "Rename every transaction from this merchant" is undoable too: the other rows' names and the alias go back. Undo puts transactions back in the inbox without re-posting notifications (the user is in the app, looking at them).
- **Drill-downs list exactly what the card counted:** a top merchant opens its merchant key (not a text search), money out only, and no drill-down shows ignored rows.
- **Select all selects what's shown**, so a collapsed inbox's hidden rows are left out.
- **Insight widget tap keeps the app's state:** the intent brings the running app to the front (`CLEAR_TOP | SINGLE_TOP`), and `MainActivity.onNewIntent` switches to the Analysis tab the way the bottom bar does, so an open sheet in the Transactions tab survives. A cold start still uses the NavHost's deep link.
- Smaller fixes:
  - The donut reads the latest selection, which is keyed by category, not slice position.
  - The ▲/▼ percentage is clamped.
  - The Quick-Sort footer actions are 48 dp tall.
  - The Insight widget drops weekday labels below 130 dp.
  - The picker survives rotation.
  - A newer snackbar replaces an older one.
  - "Report wrong amount" closes the sheet so its confirmation is visible.
- **Sheet forms keep their input** across leaving the tab and process death: form values live in a `SheetForm` saved by the composable that shows the sheet. A `ModalBottomSheet` is its own window, and `rememberSaveable` inside it was dropped (found on the emulator after a widget tap).
- **Large text (200%):**
  - The three Analysis stat cards stack full width.
  - Widget text grows only up to 1.3x, while chip and button labels in fixed-height boxes don't grow at all (they keep full TalkBack labels).
  - Insight category rows are sized for the scaled text.
- Glance widget tests run with a 15 s harness timeout instead of 2 s: the first test of a class missed 2 s under a loaded full build (a flake, not a hang).

## ADR-0026: Family seed group and versioned seeding
- **Family** (expense): Transfer, Lend; D-Mart, Kirana, Groceries; Other. The owner created it on their phone and asked for it in the seed set. New installs get it before Investments, with its own color (cyan 800).
- **Seeds are versioned per group:** each group records the seed version that introduced it, and the seeder adds only groups newer than the version an install already has. The earlier "re-run the whole list on a version bump" would have re-created seeds the user deleted.
- **Adoption instead of duplicates:** a new group first looks for an unseeded category with the same name (ignoring case) at the same level and marks it as the seed, keeping the user's name, icon, color, kind and order. Its same-named subcategories are adopted the same way; missing ones are added after the user's own. Without this, an install that already has its own "Family" would get a second one.

## ADR-0027: Release polish (M8)
- **Analysis periods** (owner's request on 2026-11-11: "total income and expenditure throughout the app duration is useless"): Week / Month / Year / Range segments. Weeks run Monday to Sunday; a year, and any range over 92 days, is charted by month. Every period compares with the one just before it, up to the same point while it's current. The category donut has a Spending / Income toggle, and income drill-downs list money in. The chosen period is kept in saved state. The Insight widget keeps its month view.
- **Backup format:** plain JSON with its own schema (`format`, `version` 1), not the Room entities, so a database migration can never silently change it. Restore replaces everything in one transaction and rolls back on broken references. Unknown keys are ignored and newer versions refused. Settings travel with it. The unparsed SMS list doesn't (it is local diagnostics). The backup records its seed version, so seed groups added later are adopted on restore (ADR-0026).
- **CSV export:** RFC 4180, signed rupees (money out is negative), category and subcategory columns. SMS bodies are left out. Text cells starting with `= + - @` get a leading quote so spreadsheets don't run them as formulas.
- **Start fresh:** deletes transactions, closed days and the unparsed list; keeps categories, merchant rules, names and accounts. It sets an import floor (`IngestCheckpointStore.startAt`) so the reconcile scan's 10-minute overlap can't bring older SMS back, and restarts day tracking. An explicit "Import older SMS" may still re-import, because the user asked for it.
- **Data replaced** (restore, start fresh) is a new `InboxListener` event. The notifier cancels every transaction notification, because a restored id may now belong to another transaction.
- **Onboarding** shows once on a fresh install. An upgrade that already has transactions skips it (decided once, stored). Every permission is optional: the step's button reads "Not now" until it's granted. The SMS step is the prominent disclosure (SCOPE.md §7). A denial without a dialog shows the restricted-settings guide with an App info button. The battery step opens App info (no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and adds the Samsung sleeping-apps tip on Samsung phones.
- **Theme:** System / Light / Dark plus wallpaper colors (Android 12+). The main activity draws nothing until settings load, so the wrong theme never flashes, and system bar icons follow the chosen theme.
- **Accounts** can be nicknamed and hidden from filters (their transactions still count). **About** checks the installed package's permissions before showing the no-internet badge.
- **Baseline profile** from a `:baselineprofile` generator (fresh install, setup, every tab). It is checked in under `app/src/release/generated/`, and profileinstaller compiles it on sideloaded installs. Generation is manual, so CI needs no device.
- **Accessibility pass:** every screen was audited on the emulator. A script reads uiautomator dumps and flags clickable nodes without a label or under 48 dp. It found two real issues, both fixed: the Transactions FAB exposed no label (Material 3's extended FAB didn't publish its text; TalkBack said "Button"), and Quick-Sort's "Remember for <merchant>" switch had no name, with its label misused as the state description. ATF checks in Robolectric tests were tried and dropped, because a deliberately broken control passed them, so they gave false confidence.
- **SMS card follows the permission:** Transactions re-checks SMS access on resume (granted in App info on the restricted-settings path) and imports at once when it appears.

## ADR-0028: Keep bank-like alerts from unknown senders
- **Problem:** the owner's Jupiter account alerts never appeared, and nothing said why. Upstream's "Jupiter" parser only matches the Edge CSB credit card sender (`XX-JTEDGE`), and SMS from senders no parser knows were dropped silently (SCOPE.md F2 step 1).
- **Decision:** a message from an unknown sender is kept in the unparsed list as `UNKNOWN_SENDER` when it clearly reads as a transaction alert. All of these must hold: a registered business header (never a phone number, never a promotional `-P` header); a rupee amount; a money-movement verb (credited, debited, spent, ...); an account word (A/c, card, UPI, ...); and no OTP or verification code. It is never parsed into a transaction or notified. The list stays capped at 200 and is user-deletable.
- **Why it's still within hard constraint 4:** such a message is a financial message by every reasonable reading, and it is exactly what the "Unparsed bank SMS" list exists for (a parser bug report). The heuristic is deliberately strict, and its tests pin the personal, promotional and OTP cases that must stay out.
- **Next step:** with one real (anonymised) sample from the list, a parser for that bank can be added in `:ingest`, or upstream.

## ADR-0029: The release build must contain every view model
- **Found** while generating the baseline profile: the release APK crashed on its first tab with `NoSuchMethodException: TransactionsViewModel.<init>`. Incremental KSP had left `:feature:transactions` with no release Hilt output, and Gradle still treated the task as up to date. So the release component lacked `TransactionsViewModel` and `QuickSortViewModel`. Debug builds were fine, and every device test so far had used debug builds.
- **Fix:** `ksp.incremental=false` (full KSP runs cost seconds). `scripts/check-hilt-viewmodels.sh` fails CI and the release workflow if a variant's Hilt component misses any `@HiltViewModel` found in the sources.
- **Practice:** before a release, install the release APK (signed with any key) on the emulator and walk through setup and every tab.

## ADR-0030: Follow-ups from the M8 review
- **Data replacement is atomic against ingestion:** restore and start fresh run under the ingest pipeline's lock and bump a generation counter. Every inbox scan records the generation when it starts, and the pipeline drops its messages once that generation is stale. A backfill still iterating when the user taps "Start fresh" can no longer refill the data. The steps after the commit (notification cleanup, settings, seeds, checkpoint) run non-cancellable, so leaving the screen mid-way can't leave notification buttons on reused ids.
- **Restoring an older backup** rewinds the scan checkpoint to the backup's time, never forward. It drops a newer "start fresh" floor and requests a scan, so SMS received since the backup come back.
- **File work** (backup, export, restore) runs on the IO dispatcher. It falls back from write mode "wt" to "w" for providers that refuse truncation, and maps any provider error to "couldn't use that file". Cancellation still propagates.
- **Onboarding is decided once:** on first launch the app stores whether setup is needed (an upgrade with data skips it). A setting written or an SMS imported during setup can no longer re-decide it.
- **Blocked SMS access** = no dialog shown (the result arrives in under 400 ms) or the rationale flag dropped (a second denial). A dismissed dialog can be asked again. The granted state comes from the system, not the result map.
- **Comparisons** use calendar arithmetic (same day last week, month or year), so leap years line up. A monthly custom range lays the previous range onto its own buckets by day offset.
- **Smaller fixes:**
  - The heuristic needs "Rs"/"INR" as a word of its own, and excludes OTPs glued to digits as well as PIN/MPIN messages.
  - The Hilt guard accepts class modifiers and annotations between, fails on unmatched annotations, and matches whole words.
  - A hidden or replaced account no longer stays as an invisible Analysis filter.
  - The nudge notification follows replaced data.
  - Copied unparsed SMS are marked sensitive on the clipboard.

## ADR-0031: Release QA follow-ups (M8 emulator round)
- **Blocker, fixed:** in the R8 release build every Quick-Sort widget button failed (`NoSuchMethodException: SortChipAction.<init>`). Glance creates `ActionCallback` classes by reflection, R8 removed their no-arg constructors, and debug builds hid it. `:widget` now ships a consumer keep rule. `scripts/check-release-reflection.sh` reads the release dex and fails CI if any `ActionCallback` lost its constructor; a build without the rule was confirmed to fail it.
- **Onboarding's notifications step** had no Allow button (the launcher was wired up but unreachable). It has one now.
- **Bank-like alerts from unknown senders** were only caught by the inbox scan, because the SMS receiver filtered on known banks alone. The receiver now uses the same rule as the pipeline (ADR-0028).
- **Deleted unparsed SMS stay deleted:** deleting records a "dismissed up to" time, and the pipeline won't record unparsed messages received at or before it. Otherwise the scan's 10-minute overlap re-added them. The trade-off: an older unparsed message not stored yet also stays out, which only matters for a later "Import older SMS". (1.0.3: single deletes are remembered by body hash instead, ADR-0049.)
- **Undo snackbar** sits above the keyboard (it was hidden behind it while search had focus). **Range picker** has a compact one-line headline ("1 Oct - 5 Nov"); the default display-size headline wrapped at normal text size and filled the dialog at 200%. **Period segments** shrink their text instead of truncating ("Mon…").

## ADR-0032: Bright category colors, darkened on light surfaces
- **Why:** the owner found the old palette (Material 800 shades: brown, indigo, deep red) too dark on the black theme, where slices and avatars almost vanished. They asked for light yellow, pink, neon shades.
- **Palette:** 12 bright shades, one per hue family (lemon yellow, neon orange, coral, hot pink, magenta, lavender, blue, sky, aqua, neon green, lime, silver). Each is at least 7:1 against black. `CategoryPalette` in `:core:model` is the single source; the editor and the seeds use it.
- **Stored value is the bright shade.** On a light surface it is drawn darker with `CategoryPalette.forLightSurface`: same hue and saturation, with HSL lightness lowered until it reaches 3:1 against white, the WCAG level for graphics. That way a lemon-yellow slice is still visible on white. The app uses `BudgetTheme.categoryColor`, and the widget uses a day/night color provider.
- **Existing data:** DB 4 -> 5 has no schema change. Its migration moves each old palette or seed color to its bright counterpart. Restoring an older backup applies the same mapping. Colors outside the old palette are kept as they are.

## ADR-0033: Translucent widgets
- **Why:** the owner found the opaque widgets heavy on the home screen.
- **How:** backgrounds are color resources with alpha (`widget/src/main/res/color*`): 66% by day and 58% at night. On Android 12+ the base is the wallpaper's Material You neutral. Because the launcher resolves the resources, day/night and wallpaper changes still need no redraw. The transaction card is a glass overlay. Ignore and the bar tracks are a quiet tint, and category chips are tonal at about 75%. The filled "More" button and today's bar stay opaque, so the main actions still stand out. Launchers can't blur behind RemoteViews, so the alpha levels keep text readable on busy wallpapers instead.

## ADR-0034: KSP tasks run one at a time
- **Found** while verifying ADR-0032: clean parallel builds failed intermittently. Two of three `--rerun-tasks` builds failed, and so did one ordinary CI run.
  - KSP reported `android.content.ContentResolver` "could not be resolved" in `:feature:settings`.
  - `:core:data:kspReleaseKotlin` "succeeded" but left no output directory, so Hilt missed `WallClock`, `AutoSortSetting` and `UnparsedDismissals`.
  - The release crash in ADR-0029 has the same shape.
- **Cause:** KSP2 runs in the Gradle daemon (`KspAAWorkerAction`). Concurrent KSP tasks share its cached analysis classloader, and with it the analysis engine's global state. Run one after the other, the same tasks never fail.
- **Fix:** a shared build service with one permit (`build-logic/.../KspSerialization.kt`), used by every KSP task through the Hilt and Room convention plugins. All other tasks still run in parallel. KSP costs a few seconds per module, and running it serially also lowers peak memory in the 9 GB build slice. `scripts/check-hilt-viewmodels.sh` stays as the backstop.

## ADR-0035: Glowing cyberpunk colors in the category palette
- **Why:** the owner wanted a cyberpunk feel: glowing neon colors and a white, in the style of common dark-mode SaaS palettes.
- **Palette:** 22 shades. The 12 from ADR-0032 stay, joined by travertine gold, amber, rose neon, electric violet, periwinkle, electric cobalt, terminal cyan, mint, electric chartreuse and a glowing white. The bar against black drops from 7:1 to 4.5:1 (WCAG AA), because the deeper neons (violet 5:1, cobalt and rose 5.7:1) are the point of the set. Colors are drawn as fills, never as body text.
- **Glow:** each swatch in the category editor sits in a halo of its own color, fading out radially, so the picker reads as neon on dark surfaces.
- **Seeds:** the default category colors follow the owner's own reassignment on their phone: Food & Drinks lavender, Shopping lemon, Housing silver, Vehicle orange, Financial expenses aqua, Family pink. Existing installs keep whatever the user picked; there is no migration.

## ADR-0036: Public open-source release
- **Why:** v1 was scoped for the developer and family. The owner decided on 2026-11-12 to publish it for everyone.
- **What changed:** the audience in SCOPE.md, plus the files a public project needs: a code of conduct, a security policy using GitHub private vulnerability reporting (sensitive, because the app reads financial SMS), a pull request template and a feature request form. The README links them.
- **What did not change:** the product, distribution (signed APKs on GitHub Releases, Obtainium-friendly, F-Droid and Play later) and the hard constraints. In particular there is still no `INTERNET` permission.

## ADR-0037: Wallet-in-a-ring launcher icon
- **Why:** the first icon (a white tray and tick on green) did not say "budget". The owner picked a wallet inside a circular budget-progress ring in the Smoky Quartz & Periwinkle palette (`#181824`, `#22223B`, `#8C92AC`, `#F2F4F8`).
- **Layers:** a radial-gradient background drawable, a foreground with a 75% progress arc, the wallet, a card and a clasp, all scaled to 92% so the ring stays inside the 66dp safe zone, and a separate monochrome drawable for themed icons (the clasp is cut out so it reads in one color).
- **Store icon:** the 512x512 PNG is the same artwork, full bleed, in `app/src/main/ic_launcher-playstore.png` and `fastlane/metadata/android/en-US/images/icon.png`. SVG sources are in `docs/icon/`.
- **No text:** no letters or currency symbols, so the icon works in every language and at 32px.

## ADR-0038: The unparsed list keeps only transaction-like bank SMS, masked
- **Why:** v1.0.1 kept every message a known bank sent that the parser did not match, so OTPs, loan offers and EMI reminders were stored in full (audit F9, against hard constraint 4).
- **Rule:** a known bank's unmatched message is kept only if its body reads like a completed transaction (rupee amount, movement word, account word, no OTP/PIN wording, no "is due", "will be debited", "bounced", "declined", "requested", "pre-approved", "apply now"). Otherwise the parser returns `SmsParseResult.Ignored` and nothing is stored. "Failed" is not in the list because reversals say "credited ... for failed UPI txn".
- **Masking:** kept bodies go through `UnparsedRedactor` (`:ingest`): links, UPI IDs/emails, all but the last 2 digits of account, card, reference and phone numbers. Amounts, dates and wording stay so the message is still useful for a parser fix. Names are not detected; the screen asks the user to remove them before sharing. `bodyHash` stays the hash of the original body so rescans still dedupe.
- **Existing data:** `IngestPipeline.scrubUnparsed()` runs at the start of every reconcile, deletes rows that the rule no longer keeps and masks the rest. Idempotent, at most 200 rows. No schema change.

## ADR-0039: Screenshots and recents hidden by default
- **Why:** amounts showed in the recent-apps thumbnail, screenshots and screen sharing (audit F1). The owner asked for protection on by default.
- **How:** `MainActivity` and `QuickSortActivity` set `FLAG_SECURE` in `onCreate` before the first frame, then follow `SettingsStore.secureScreen` (Settings > Privacy > "Hide amounts in screenshots & recents", default on). `FLAG_SECURE` also blanks the recents card, so `setRecentsScreenshotEnabled` is not needed.
- **Trade-off:** with it on, the user's own screenshots of the app are black too; that is why it is a switch. Widgets and notifications are drawn by the launcher and system UI and are not covered.
- **Not in backups:** a per-phone privacy choice; a restored phone starts with it on.

## ADR-0040: Money in green, money out red, everywhere, with a real minus sign
- **Why:** in v1.0.1 money out was red only on inbox cards and the edit sheet, and neutral elsewhere. The owner wants one rule on every screen: outgoing red, incoming green. (The audit brief first asked for neutral; the owner corrected that before release.)
- **How:** `formatSignedInr` (`:core:model`) is the one place a directional sign is built: `+` for in, U+2212 `−` for out (same width as `+` in tabular figures); `formatInr` also uses `−` for negative values, so color is never the only signal. `MoneyText` colors every directional amount (no per-caller switch); the Quick-Sort sheet, both widgets, Analysis (month comparison chip, Net) and the notification accent follow the same rule.
- **Red also stays** for errors, Delete and the Overdue badge.

## ADR-0041: One bottom-sheet scaffold that keeps scrolling inside the sheet
- **Why:** Material 3 1.4's `ModalBottomSheet` gives its content's leftover scroll to the sheet (`onPostScroll`) and settles the sheet after every fling (`onPostFling`, even at zero velocity). With `skipPartiallyExpanded` the only anchors are Expanded and Hidden, so scrolling a long form back to the top a little too hard closed it and lost the input (reproduced on the emulator and the phone, TEST_REPORT item 3), and small scrolls near the top could move and re-settle the sheet (the likely cause of the Quick-Sort "More" jitter, item 2).
- **How:** `BudgetBottomSheet` (`:core:designsystem`): pinned `header` (title, primary action), scrolling `content`, optional pinned `footer`. When the content is taller than the sheet, a `NestedScrollConnection` between the content and the sheet consumes the leftover scroll and fling, so only the drag handle and the header move the sheet. A short sheet keeps swipe-to-dismiss. Every sheet uses it; no feature calls `ModalBottomSheet` directly.
- **Unsaved changes:** `hasUnsavedChanges` vetoes the Hidden target (`confirmValueChange`) and shows "Discard changes?". Back is handled in `onDismissRequest`, because Material calls it after a Back press even when the hide was vetoed. Used by the category editor; the transaction forms rely on the scroll fix for now.
- **Insets:** the sheet already pads its content with `safeDrawing` (top and bottom, which includes the keyboard), so contents no longer add `navigationBarsPadding()`/`imePadding()`.
- **Tests:** `BudgetBottomSheetTest` fails if the scroll connection is removed.

## ADR-0042: Transfers (loan EMIs, card bills, own-account moves) are never spending or income
- **Why:** in v1.0.1 one loan EMI counted as an expense (the bank debit, typed INVESTMENT) and as income (the loan account credit), a self transfer counted on both sides, and a card bill counted as spending on top of the card spends it pays for (TEST_REPORT item 5). The owner wants each of these as one transfer, left out of income and expense totals.
- **Supersedes** the ADR-0018 idea that Self Transfer out (Expense) and Self Transfer in (Income) "cancel out": both totals were inflated. Those two categories stay for people who use them; new transfers go to the new group.
- **Categories, not a new exclusion:** a seed group "Transfers" (kind TRANSFER, seed version 3) with Between my accounts, Loan EMI, Credit card bill, Loan received. Every total already leaves TRANSFER-kind categories out, so no query changed. Upgrades and old backups get the group from the seeder; user categories are not touched.
- **Rule layer (`FlowRules`, `:ingest`):** runs after parser-core, which stays unmodified. Sets `ParsedSms.flow` and type TRANSFER for: a bank debit with EMI/loan wording, an ACH payee in a lender list, or a card-bill UPI handle or wording (LOAN_REPAYMENT, CARD_BILL_PAYMENT); a loan account or credit card being credited (the other side of those payments); "LOAN DISBURSEMENT" credits (LOAN_DISBURSAL). It also names the payee of HDFC "ACH D- <NAME>-<ref>" debits. The same confirmation wording in an unparsed or unknown-sender message is not kept.
- **Linking (`IngestPipeline`):** both legs get `transactions.linkedToId` (schema 6, auto-migration 5 -> 6). Same reference (6+ characters), same amount, opposite direction, another account, within 3 days: own transfer. A loan or card leg and exactly one opposite leg of the same amount within 3 days: the two sides of that payment. Ambiguous matches link nothing. A linked or hinted transaction is sorted into its Transfers category (auto-categorized, no notification); a still-waiting other leg is sorted too. If the category is archived or has subcategories, it waits in the inbox.
- **Backups:** `linkedToId` is an optional field; format version stays 1 (older apps ignore it, older files restore with no links).
- **Tests:** golden cases with a `flow` key, `TransferLinkingTest` (pipeline), `TransferChainTest` (real parser and pipeline on the probe chains), `MigrationTest` 5 -> 6.

## ADR-0043: "Help improve the parser": a read-only 90-day scan, shared by the user's mail app
- **Why:** the owner needs real examples of bank SMS the parser misses, without touching anyone's budget and without the app gaining network access.
- **Read-only by construction:** `ParserFeedbackScanner` (interface in `:core:data`, implementation in `:ingest`) gets only the SMS inbox reader and the parser. It has no database, notification or WorkManager dependency, calls `SmsParser.parse` directly, never `IngestPipeline`. The result lives in the screen's view model only; process death drops it.
- **What is kept:** exactly what the unparsed list would keep (ADR-0038): a known bank's unmatched transaction alert, or a transaction alert from an unknown business sender. OTPs, offers, reminders, payment confirmations and personal SMS never qualify. Bodies go through `UnparsedRedactor`; identical masked texts are merged with a count; only the month is kept, not the time. At most 300 messages.
- **Sharing:** `ACTION_SEND` with a `mailto:` selector (only mail apps are offered), the report in `EXTRA_TEXT`, addressed to the owner (`feedback_email_address`, public in the source). Text in the body avoids a `FileProvider` (no new exported surface). *Save file* uses the Storage Access Framework. No new permission. GitHub issues were rejected: public, and they would need network access or a token.
- **Not masked:** names. The preview says so and lets the user remove messages. (Superseded in 1.0.3 by ADR-0046: names and shops are masked.)
- **Developer side:** docs/PARSER_IMPROVEMENT.md and `scripts/feedback-to-golden.py` turn a report into golden test stubs for a coding agent to fill.

## ADR-0044: Password-protected backups with PBKDF2 and AES-256-GCM from the platform
- **Why:** a backup holds every transaction, merchant and SMS body in plain JSON (audit F2). 1.0.2 only warned; the owner moved encryption into 1.0.3.
- **Optional, off by default:** the Back up dialog has *Protect with a password* (8+ characters, typed twice). A forgotten password loses the backup, so it is a choice, and the plain-file warning still shows when it is off. CSV export stays plain.
- **Format:** one JSON object, `format: budget-widget-backup-encrypted`, `version`, `kdf` (`PBKDF2WithHmacSHA256`), `iterations` (600,000, OWASP 2023), 16-byte `salt`, `cipher` (`AES/GCM/NoPadding`), 12-byte `nonce`, `ciphertext` (Base64, tag included). The plaintext is the normal backup JSON, so restore logic is shared. Every header field is GCM associated data: editing one fails like a wrong password. A file's iteration count must be 100k-10M, so a crafted file can't stall the app. Readers refuse unknown algorithms and newer versions.
- **Only `javax.crypto`:** no library (Tink, SQLCipher) for one file format. Key derivation runs on `Dispatchers.Default`.
- **Restore:** detects the format, asks for the password, keeps asking after a wrong one ("Wrong password, or the file was changed": GCM can't tell the two apart). Plain backups from every earlier version restore as before.
- **Passwords stay in memory:** held in `remember`, not `rememberSaveable` (saved state can reach disk), and in the view model only while the file picker is open. If the process dies meanwhile, the picker result writes nothing and says "try again", rather than silently saving an unprotected file. Char arrays are cleared after use (best effort: Compose text fields hold `String`s).
- **Tests:** `BackupCryptoTest` (round trip, unique salt and nonce, wrong password, flipped and cut bytes, tampered header, unsupported headers), `DataTransferTest`, `SettingsViewModelTest`, `SettingsScreensTest`.

## ADR-0045: "Keep original SMS text" drops stored SMS after 90 or 30 days
- **Why:** stored SMS bodies are plaintext in the private database (audit F3). SQLCipher is not worth it (SECURITY_AUDIT section 4: about 4 MB per ABI, and the key sits on the same unlocked phone as the SMS inbox). Keeping less is the better control.
- **Setting:** *Settings → Privacy → Keep original SMS text*: Forever (default, so nothing changes on upgrade), 90 days or 30 days. Kept on this phone only, not in backups, like *Hide amounts in screenshots*.
- **What goes:** `smsBody` of transactions that are sorted (categorized or ignored) and happened (`occurredAt`) before the limit. Transactions still in the inbox keep their SMS, since sorting may need it. Every parsed field stays, and `updatedAt` doesn't move (it isn't an edit). Unparsed entries older than the limit are deleted, since they are nothing but their SMS. The phone's own SMS inbox is never touched.
- **Safe for de-duplication:** idempotency is `txnHash`, not the body, so a later reconcile or import never re-adds a transaction whose text was dropped. "Report wrong amount" and the SMS block on the edit sheet already handle a missing body.
- **When:** `SmsBodyRetention.apply()` runs in `ReconcileWorker` after every scan (periodic, on app start and after imports), and right away when the setting is shortened. Idempotent; one `UPDATE` and one `DELETE` in a single database transaction.
- **Tests:** `SmsBodyRetentionTest` (forever keeps all, 30 and 90 day cut-offs, pending kept, only the body changes, idempotent), `SettingsViewModelTest`, `SettingsScreensTest`.

## ADR-0046: Parser reports are masked with a word allowlist, keeping the shape
- **Why:** 1.0.2's parser report reused the unparsed-list masking (numbers only) and asked the user to spot names themselves. A blocklist can't know every name or shop, so the shared text now passes an allowlist (plan C2).
- **Rules (`SmsRedactor` in `:ingest`):** a message with a secret (OTP, CVV, PIN, password) is dropped. Links become `<URL>`, anything shaped `x@y` becomes `<UPI>`. Every digit becomes `9`, except the last 2 of an account or card number (after `a/c`, `acct`, `account`, `card`, `ending` or the bank's own `XX`/`*` mask). A word is kept only if it is in `RedactorVocabulary` or is one letter; any other word becomes `x`/`X` of the same length and case. Styled Unicode letters are compared in NFKC form, and digits are masked by code point, so styled or Devanagari digits can't slip through.
- **Shape is kept** (lengths, separators, case, positions), so a report still teaches the template. With *Keep real amounts* (off by default), the masked golden corpus parses to the same type, amount and direction (tested).
- **Vocabulary:** about 500 hand-reviewed words: banking terms, rails, banks and card networks, months, weekdays and function words. Seeded from words that recur in the golden corpus, the probe set and upstream's test SMS. Merchants are deliberately absent: where someone shops is personal. Single letters stay ("A/c", "D-").
- **Keep real amounts:** a checkbox on the intro, off by default. It keeps rupee amounts that don't follow a balance or limit word; balances and limits stay masked.
- **Scope:** only the shared report. The unparsed list on the phone keeps its lighter masking (ADR-0038), because the owner needs to recognise those messages. `scripts/feedback-to-golden.py` now accepts runs of 9s and still refuses real digit runs and `@`.
- **Secrets:** `password` joins the secret words, which also keeps such messages out of the unparsed list.
- **Tests:** `SmsRedactorTest` (each rule, styled text, idempotence, a property check over the golden corpus and probe set: no `@`, no unmasked 6+ digit run, every word known or masked; masked golden cases still parse the same), `InboxParserFeedbackScannerTest`, `ParserFeedbackTest`.

## ADR-0047: Parser reports are JSON Lines in the email body, with no attachment
- **Format:** one JSON object per message, `{"v":2,"app","parser","sender","reason","count","month","body"}`, written by `ParserFeedbackReport.jsonLines` (kotlinx.serialization, so quotes and newlines in a body are escaped and each message is exactly one line). Every line carries the app version and parser commit, so reports from several phones can be concatenated. Replaces 1.0.2's text format, whose `--- n | ...` separators a body could have imitated. `scripts/feedback-to-golden.py` reads both.
- **Email:** the body is one line of explanation, then the same lines. No `FileProvider` attachment: it would be the app's first content provider (more manifest surface for the audit and the CI checks), and text in the body already works with Gmail (checked on the phone for 1.0.2). At most 300 messages, so the body stays a reasonable size. The script skips non-JSON lines and joins lines a mail app wrapped.
- **Save file:** `budget-widget-parser-feedback.jsonl`, created as `application/octet-stream` because JSON Lines has no registered MIME type and pickers append their own extension for `application/json` or `text/plain`.
- **Remove all from a sender:** the preview lists each sender with a count and a *Remove all* button, shown when there is more than one sender or a sender has several messages.
- **Tests:** `ParserFeedbackReportTest`, `InboxParserFeedbackScannerTest`, `ParserFeedbackTest`.

## ADR-0048: "Add as transaction" on unparsed bank SMS, instead of user-written templates
- **Why:** plan B5 judged "Teach this message" (tag tokens, compile a template) large and brittle: one sample makes a fragile rule and a wrong tag means silent wrong amounts. Parser fixes go through the improvement loop (docs/PARSER_IMPROVEMENT.md). What the user needs today is to get that one payment into the budget.
- **What:** each entry with text in *Settings → Unparsed bank SMS* has *Add as transaction*. It opens the normal manual-add sheet over the list, prefilled by `SmsPrefill` (`:core:data`): the first rupee amount, money in or out from the first movement word ("debited ... credited to X" is money out), and the SMS time. The SMS (sender and masked body) is shown at the top of the sheet while filling it in. Category stays the user's choice.
- **Stored as:** a `MANUAL` transaction (amount and time stay editable) with `smsSender`/`smsBody` set, so the edit sheet shows its source. *Report wrong amount* is hidden for it: the user typed that amount. The body is the masked unparsed text, already a financial message (constraint 4), and E6 retention applies to it like any other.
- **The entry leaves the list** through the same path as deleting it (`deleteUnparsed`, a dismissal), so the next inbox scan doesn't bring it back.
- **Not handled:** if a later parser update reads the same SMS during an import, the SMS transaction and this manual one may both exist. Rare (imports are user-started), visible, and fixable by deleting the manual one.
- **Wiring:** the settings feature doesn't depend on the transactions feature, so `UnparsedRoute` takes an `addSheet` slot that the app's nav host fills with `AddFromSmsSheet` (`:feature:transactions`), which has its own view model. The prefilled form is saved under its own key, apart from a half-typed cash entry.
- **Tests:** `SmsPrefillTest`, `TransactionEditorTest`, `AddFromSmsTest` (prefill, save moves the entry, missing entry, prefilled form), `SettingsScreensTest`.

## ADR-0049: Deleted unparsed SMS are remembered by body hash, not only by time
- **Bug (since the time mark, found during the B5 device check):** an unparsed entry recorded by the SMS receiver came back after the next inbox scan once deleted. The receiver stamps an SMS with the PDU's sent time (`SmsMessage.timestampMillis`); the scan reads the provider's arrival time (`Telephony.Sms.DATE`), usually a second or more later. Deleting set the mark to the stored (sent) time, so the scan saw the same message as newer than the mark and recorded it again. On the emulator: sent ...224000, arrival ...225076, back after relaunch. The pipeline test used one time for both reads, so it never showed.
- **Fix:** a delete remembers the entry's body hash (`IngestCheckpointStore`, newest 500, about 32 KB), and the pipeline skips an unparsed message whose hash is dismissed, whatever time it is read with. The unparsed table already de-duplicates on the same hash. *Delete all* dismisses every listed hash and still moves the time mark (to the newest listed time), for older messages that could come back through an import. 1.0.2 marks stay honoured.
- **Side effect, good:** deleting one entry no longer hides older unparsed messages that weren't recorded yet.
- **Not changed:** the two time sources. Transactions are matched by `txnHash`, so moving either reader to the other clock would risk duplicates for no gain here.
- **Tests:** `IngestPipelineTest` (receiver time then a later scan time, for one delete and for delete all; other messages still recorded), `IngestCheckpointStoreTest` (cap and de-duplication). Device: three entries whose arrival time was after their sent time (two deleted, one added as a transaction) stayed gone after a relaunch scan.

## ADR-0050: Dependency verification, signed build provenance and a reproducible APK
- **Why:** audit F4 (no Gradle dependency verification) and F5 (no provenance, reproducibility unconfirmed). F-Droid and IzzyOnDroid (plan D6) also want a reproducible build.
- **Dependency verification:** `gradle/verification-metadata.xml` holds the SHA-256 of all 881 components (artifacts and their metadata) that the CI and release task sets resolve, plus `:app:bundleRelease` and `:baselineprofile:assemble`, including plugins and `build-logic`. Strict mode; signatures off (sha256 is what the plan asks for, and many Android artifacts aren't PGP-signed). IDE `-sources`/`-javadoc` jars are trusted by name only. Checked: a full `--rerun-tasks` build of the CI task set passes, and one changed hash fails with "Dependency verification failed". Refresh steps in CONTRIBUTING.md. Note: a local configuration-cache hit skips re-resolution, so test changes to the file with `--no-configuration-cache`; CI runners start with no configuration cache and the release job disables it.
- **Provenance:** `release.yml` runs `actions/attest-build-provenance` (v4.2.2, pinned by SHA) on the APK and the source archive, with `id-token: write` and `attestations: write` on the release job only. Verify with `gh attestation verify budget-widget.apk -R SwashBuckler104/budget-widget`.
- **Reproducible:** `vcsInfo.include = false` (the source archive has no `.git`, so the commit file differed) and `dependenciesInfo` off (an encrypted blob only Google can read; F-Droid asks for it off). `scripts/compare-apks.py` compares two APKs entry by entry, ignoring only signature data. Checked: v1.0.2 rebuilt locally from its sources, with no build cache and every task rerun, at another path, matched the published APK in all 1,123 entries except the commit file. The 1.0.3 build from a copy of the tracked files elsewhere is byte-identical to the repository build. Procedure: docs/REPRODUCIBLE_BUILD.md.
- **Not done here:** an automatic rebuild-and-compare job in CI (it would double the release time); the check is documented and run by hand at release time, and by F-Droid if listed.

## ADR-0051: "Midnight iris" brand palette as the default look; wallpaper colors become opt-in
- **Why:** the owner found the 1.0.2 look bland and unfinished. Reviewed on the owner's setup (dark mode, Android's tonal-spot wallpaper palette): every surface was the same grey-lavender, cards barely separated from the background, spending showed as washed pink, and the brand palette was never seen because wallpaper colors were on by default.
- **Palette (`BrandColors`):** ink-blue surfaces in clear steps (`#0B0E17` background up to `#262D42`), an electric iris accent (`#9AA8FF` dark, `#3D4BD8` light) and amber as the second voice (streak chip, chart reference lines). The accent is deliberately neither green nor red, so it can't be read as income or spending; the old teal sat too close to the income green. Dark money colors are stronger: income `#4BE38F`, spending `#FF7B7B`.
- **Default:** `dynamicColor` now defaults to off, so the app has one identity out of the box. *Settings → Wallpaper colors* still turns Android's palette on. An install that never touched the switch moves to the brand palette on update; one that set it explicitly keeps its choice. Backups always carry the value, so a restore keeps what was saved.
- **Depth:** cards get a hairline (`BudgetTheme.cardBorder`), because tonal steps alone look flat on dark. The Analysis "Spent" card uses `BudgetTheme.heroBrush` (accent container fading into the surface) with an accent edge; its change pill is a dark chip with green or red text instead of a red block on indigo.
- **Screens:** inbox cards lead with a quiet disc holding a red up-right or green down-left arrow, so pending items line up with the sorted rows below; *Sort all* is the one filled button. Analysis status chips are tinted (amber streak, accent inbox). Settings sections sit on cards instead of a divider-separated list. Category chips are filled.
- **Not changed:** the category palette (ADR-0035) and stored category colors, the money-color rule (ADR-0040), and widgets, which still follow the wallpaper on Android 12+ with translucent surfaces (ADR-0033).

## ADR-0052: Category icons are line icons in the category's color, on a tile of the same hue
- **Why:** the owner found the category marks unfinished and hard to read: a small full-color emoji on a bright, nearly solid disc. The emoji's own colors fought the disc (a yellow house on a yellow disc), each emoji had a different visual weight, and the editor's color swatches glowed while the marks themselves were flat.
- **Looked at:** the two common patterns in budgeting apps. One is a single-color glyph on a colored shape (the Wallet-style taxonomy our seeds follow); the other is a user-typed emoji with no fixed icon set (Toshl, Fineyo offers both). A glyph set is the consistent one; emoji stay as the escape hatch.
- **What:** `CategoryIcons` maps each seed and editor emoji to a Material rounded icon. `CategoryAvatar` draws that icon in the category's color on a rounded-square tile: a deep shade of the same hue on dark, a pale one on light (`CategoryPalette.container`, fixed lightness, capped saturation), with a thin edge in the color. An emoji without an icon is drawn as itself on the same tile. Category chips use the same glyph.
- **Storage unchanged:** a category still stores one emoji. No migration, backups stay compatible, and widgets and notifications, which can only show text, keep the emoji. The editor's "type any emoji" field still works.
- **Editor:** the icon grid shows the glyphs on tiles, the chosen one in the category's color. Color swatches are plain dots with a ring on the chosen one; the radial glow from ADR-0035 is gone, so the picker and the result look alike. The palette itself is unchanged.
- **Tests:** `CategoryPaletteTest` (tiles opaque, deep or pale, hue kept), `CategoriesUiStateTest` (every editor emoji has an icon).

## ADR-0053: A wider icon set behind "More icons"; Analysis moves to the middle tab
- **Icons:** `CategoryIcons` now covers 111 emoji, offered in the editor in eight themed groups (`CATEGORY_ICON_GROUPS`: food, transport and travel, shopping, home and bills, health, fun and learning, people and other, money). The owner wanted the wider choice without a long form, so the editor shows 14 everyday icons (plus the chosen one when it isn't among them) and a *More icons* button that opens the groups. All keys are Emoji 5.0 or older, as before (ADR-0052: storage is still the emoji).
- **Tabs:** the bar order is Transactions, Analysis, Categories (owner's choice); it was Analysis, Transactions, Categories. The start screen is still Transactions.
- **Tests:** `CategoriesUiStateTest` (every offered emoji has an icon, no duplicates, the short list is part of the full one, at least 100).

## ADR-0054: The rest of the 1.0.3 design pass: widgets, notification buttons, chart colors, filters, lists
- **Widgets wear the brand palette** on every Android version, where they followed the wallpaper on Android 12+ (`color-v31` resources removed). The background is nearly opaque (0.88 dark, 0.92 light; it was 0.58 and 0.66): on a busy wallpaper the owner's "All sorted" line was hard to read. This replaces the translucency of ADR-0033; a little wallpaper still shows. Widgets don't follow the app's *Wallpaper colors* switch: they are drawn by the launcher from static resources, and one look is simpler than two.
- **Widget icons:** Glance shows only bitmaps and resources, so `CategoryIconBitmaps` draws an icon's vector paths once into a 96 px white bitmap (cached), tinted by the widget with the category color. Emoji without an icon stay text.
- **Notification buttons** show the category name alone. They are text-only, and an emoji there no longer matches anything in the app.
- **Chart colors:** donut slices and proportion bars use `CategoryPalette.forChart` on dark surfaces (same hue, saturation capped at 0.72 and lightness at 0.66), so a neon lemon slice doesn't outshine the screen. Icons keep the full color; stored colors are untouched.
- **Filters:** the chip row on Transactions is behind a *Filters* button in the top bar, with a badge counting active filters. While any filter is on the row stays visible, so a filter is never applied unseen. The category chip shows the name without the emoji.
- **Category list:** each group is a card; a collapsed group previews its subcategories' icons.
- **Onboarding and About:** each onboarding step has an emblem tile, its points sit on cards, and the progress dots mark the current step with a pill. About's sections are cards with their link inside.
- **Checked on the emulator** (dark theme): every screen above, the seven onboarding steps and the Quick-Sort widget on the launcher. The Insight widget is covered by `InsightWidgetTest` only.

## ADR-0055: SMS text on parsed transactions is stored masked
- **Why:** the unparsed list has been masked since 1.0.2 (ADR-0038), but a parsed transaction kept its SMS body verbatim, so UPI IDs, full reference numbers and phone numbers sat in the database, went into every backup (plain by default, ADR-0044), and were copied unmasked into the unparsed list by *Report wrong amount*. The README already promised that "kept bank messages have account, card, UPI and reference numbers masked". Retention (ADR-0045) limits how long the text lives; masking limits what it says, and the two stack.
- **How:** `IngestPipeline.store` runs `SmsParser.redactUnparsed` before insert. Amounts, dates and wording stay, so the edit sheet still shows what was parsed; nothing re-parses a stored body (idempotency is `txnHash`, ADR-0045). Rows written by 1.0.3 and older are masked by the reconcile scrub that already maintains the unparsed list (idempotent, once per process, `updatedAt` untouched). `DataTransfer.restore` masks bodies arriving from older backup files, and `TransactionEditor.reportWrongAmount` masks what it files, so the unparsed list never holds unmasked text whatever the source.
- **Not changed:** `bodyHash` for *Report wrong amount* entries is still the hash of the stored body; those entries never meet the pipeline's dismissal hashes (ADR-0049), which hash the raw inbox text of unparsed SMS.
- **Tests:** `IngestPipelineTest` (masked at insert, old rows scrubbed), `DataTransferTest` (old backup masked on restore), `TransactionEditorTest` (report files masked text).

## ADR-0056: Restore reads at most 64 MB
- **Why:** restore read the picked file whole (`readBytes`) before looking at it, so a huge or endless file (a crafted "backup", or the wrong file picked) could exhaust memory and crash the app. The encrypted path already bounds its key-derivation cost (ADR-0044); this bounds the read.
- **How:** `DataTransfer.restore` reads at most `MAX_RESTORE_BYTES` + 1 bytes; one byte over means `RestoreResult.TooLarge` ("That file is too large to be a backup. Nothing changed."), before anything is parsed or replaced. 64 MB is far above any real backup: tens of thousands of transactions fit in a few MB.
- **Also:** the parser report's *Save file* now opens the document truncating (`"wt"`, falling back to `"w"` like backup and CSV), so overwriting a longer file leaves no tail of it behind.
- **Tests:** `DataTransferTest` (an endless stream is read exactly to the cap and refused; data unchanged).

## ADR-0057: Bottom sheets keep the status bar outside the sheet
- **Why:** the Quick-Sort sheet shook up and down for about a second after a swipe up (owner's video, S23, still there after ADR-0041). Material 3 1.4's `ModalBottomSheet` consumes `WindowInsets(top = sheetOffset)` and pads its content with the rest of the `safeDrawing` top inset, so a sheet that overlaps the status bar gets taller. A nearly full-height sheet (the S23's density, or a long form) rests just below the status bar; the default spatial spring overshoots it after a fling, the content grows, the Expanded anchor (`fullHeight - sheetHeight`) moves, the offset moves, the padding changes again, until the spring dies out.
- **How:** `BudgetBottomSheet` pads the sheet itself with `safeDrawing.only(Top)` (outside `ModalBottomSheet`'s anchors) and passes `contentWindowInsets = safeDrawing.only(Bottom)` (navigation bar and keyboard). The sheet's height no longer depends on its offset, and a full-height sheet stops below the status bar.
- **Checked on the emulator** (1080x2340, density 480, where the sheet nearly fills the screen like on the S23): frames rendered in 2.5 s after a fling up went from about 80 (visible shaking in a screen recording) to 16-18, the same as a normal settle; density 420 and 500, drag down, and a fling with the keyboard open behave normally.

## ADR-0058: Put a whole inbox under Other in one go
- **Why:** importing 30 or 90 days on setup leaves hundreds of payments to sort one by one (owner, 2.0.0). Most people want the history for the charts, not to categorize each old payment.
- **Categories:** a new top-level leaf **Other** (EXPENSE, seed version 4, adopts a top-level "Other" the user made). Money coming in goes to the existing **Income › Other**. `CategorySeeder` can now also add a new subcategory to a group an install already has (`SeedCategory.since`), skipped when the user deleted the group.
- **`BulkSorter.fileUnderOther(onlyBackfill)`:** sorts every PENDING transaction by direction in one database transaction (what the parser read as a transfer, such as a wallet top-up, goes to Transfers › Between my accounts instead, so it never counts as income), then reports each sort like any other (notifications cancelled, widgets refreshed). Merchant rules don't learn from it (Other says nothing about the merchant); possible duplicates stay in the inbox (they need a look); a direction whose category was deleted, archived or given subcategories stays too.
- **Where:** onboarding's history step has *Put imported payments under Other* (off by default; hidden for Start fresh). The backfill job applies it after the import (`ReconcileWorker`), since the import runs in the background after setup closes, to every pending payment that happened before setup finished: the app-start scan may already have stored the last day as live rows (seen on the emulator; on a phone after a restart during setup), and payments made since stay in the inbox. The inbox header has **All to Other** next to Sort all, with a confirmation (no Undo: it spans two categories; the rows can be re-sorted from Other).
- **Tests:** `BulkSorterTest`, `CategoryTest` (new subcategory on upgrade, not for a deleted group), `OnboardingScreenTest`, `TransactionsScreenTest`, `TransactionsViewModelTest`.

## ADR-0059: Starting balances are a baseline per account, never income
- **Why:** the owner wants to enter each account's current balance after setup as a baseline. Recording it as income would show a 50,000 starting balance as 50,000 earned that month, so it is its own entry (owner's choice, 2.0.0).
- **How:** `OpeningBalances` keeps at most one manual transaction per account (`txnHash = opening-balance:<accountId>`) in **Transfers › Opening balance** (TRANSFER, seed version 4), dated when it is set and already sorted. Every total leaves TRANSFER categories out (ADR-0042), so no query changed; the entry shows in the list and sets the account's balance. Negative means money owed: a card's field reads "Amount owed at the start" and is stored as money out. An empty field removes it. Without the seeded category (the user deleted Transfers), a top-level TRANSFER "Opening balance" takes the seed key.
- **Where:** *Settings › Accounts*: the edit dialog has the nickname and the starting balance; a row shows "Started at ₹…" or "Started owing ₹…". The balance is written only when it changed, so renaming never resets a newer balance from SMS. The inbox shows **Add your starting balances** (Add balances, Not now) once an account is known (accounts appear with the first bank SMS, so a Start-fresh install asks later), until any account has one or it is dismissed (kept on this phone only).
- **Tests:** `OpeningBalancesTest`, `SettingsScreensTest`, `TransactionsScreenTest`.

## ADR-0060: Update check by hand-off, still no internet permission
- **Why:** the owner wants an "update available" prompt like other apps. Knowing that a newer version exists needs the network, and the app ships without `INTERNET` (SCOPE.md pillar 4, CI-enforced). The owner chose a hand-off that keeps the app offline (2.0.0).
- **Check for updates** (*About*, and the reminder) opens the page that knows: the Play listing (`market://`) when Play installed the app (`getInstallSourceInfo`, API 30+), otherwise the GitHub releases page, in the browser. If no app can open the store link, the release page is tried.
- **Reminder:** `UpdateReminder` asks "Check for a newer version?" once this version has been installed (`PackageInfo.lastUpdateTime`) for 60 days, as a note from Purple at the top right of the main screen (ADR-0063; first a dialog); Check or Later quiets it for 30 days (kept on this phone only). An update resets the clock. It uses the install time rather than a build timestamp, which would break the reproducible build (ADR-0050), and needs no release step.
- **Later:** Play's in-app updates API runs inside the Play Store app (no `INTERNET` here) and can replace the reminder for Play installs once D (Play distribution) ships, if it stays free of proprietary dependencies for F-Droid.
- **Tests:** `UpdateReminderTest`, `UpdateCheckTest`, `SettingsScreensTest` (About link).

## ADR-0061: Our own formats for senders parser-core doesn't know, and a parser skill
- **Why:** the owner's first parser feedback (2.0.0, `docs/parser-dataset.md`, kept local) showed real money movements no upstream parser claims: NKGSB Co-op Bank, the Indian Railways R-Wallet and the CRED wallet, kept only as unknown-sender entries. Waiting for upstream leaves them out of the budget.
- **`LocalFormats`** (`:ingest`): a short list of formats, each a TRAI header and exact wording, tried only for senders with no upstream INR parser and under the same sender rules (no phone numbers, no promotional `-P` headers). Rupees become paise through the mapper's one conversion. Upstream stays the parser for every sender it knows; no `com.pennywiseai` type is involved; every format has golden cases and a draft in `docs/upstream/0006`, and is deleted once upstream parses those cases.
- **Meaning:** NKGSB credits are money in and debits money out, with the account's last digits and balance. Wallet debits are expenses (an R-Wallet debit is a train ticket, merchant Indian Railways). Wallet credits are transfers in left in the inbox: a top-up from the user's bank, cashback and a refund read the same, so the user sorts them.
- **Not parsed:** Kotak card spends in USD stay in the unparsed list (v1 tracks INR and can't convert offline; *Add as transaction* takes the INR amount from the statement). Shop credit ("CLiQ Cash", coins, reward points) is not money in an account and is no longer kept (`FinancialSmsHeuristic`).
- **The loop:** `scripts/feedback-to-golden.py` now reads every report in one growing file (v1 text and JSON Lines) and keeps a local, gitignored ledger so a message is turned into a stub only once. The project skill `improve-parser` (`.claude/skills/`) runs the whole loop: new messages to golden stubs, expected values read from the text, fixes in our layer (rules, heuristic, `LocalFormats`), upstream drafts, tests.
- **Tests:** golden `nkgsb.txt`, `irctc.txt`, `cred.txt` and a Kotak USD case; `FinancialSmsHeuristicTest` (shop credit); `PennyWiseSmsParserTest` (sender rules for local formats); `SmsRedactorTest` (masked reports of these formats still parse).

## ADR-0062: "Always" for a merchant, earlier and new payments
- **Why:** the owner came from PennyWise, where one choice can apply to all previous and future transactions of a merchant. Here a rule only suggested, and auto-sorted only with the auto-sort setting on and 3+ confirmations (F8), and never touched the past.
- **What:** `merchant_rule.always` (schema 7, default off; backups carry it, older files read as off). Set from the *Always for X* switch in Quick-Sort and the edit sheet, the switch on each rule in Categories › Merchant rules, or Purple's offer (ADR-0063). Turning it on sorts the merchant's other payments into the category in one transaction: those in the inbox and those sorted elsewhere (notes kept, marked auto so *Review auto-sorted* shows them). New payments are then sorted at ingest whatever the auto-sort setting says.
- **Left alone:** ignored payments, possible duplicates still in the inbox (they need a look), legs of a transfer between own accounts, and money moving the way the category can't hold: a refund from the shop stays in the inbox. The same kind check now also guards plain auto-sort, which could put a refund into an expense category before.
- **Ending it:** sorting the merchant into another category with *Remember* on, turning the switch off in Merchant rules, or deleting the rule. The same category again keeps it.
- **Awareness (SCOPE.md F8):** auto-sort stays off by default; "always" is an explicit choice per merchant, named in the switch, and its results show the auto tag.
- **Tests:** `SortTransactionUseCaseTest` (what moves, what stays, ending it), `IngestPipelineTest` (always with auto-sort off; refund), `MigrationTest` (6 to 7), `QuickSortContentTest`.

## ADR-0063: Purple, a small in-app helper
- **Why:** the owner wants people guided through a simple app without a dense manual: a short tour of the three tabs and settings, how-tos when a feature first matters, the update reminder in a corner instead of a dialog, and a nudge to the parser feedback that most people never find.
- **What:** `Purple` (`:core:data`) picks at most one note at a time from stored state; `PurpleHost` (`:feature:purple`) shows it as a small speech bubble over the tab screens, never over Settings or a drill-down. Tour steps point at their tab above the bottom bar; everything else sits at the top right, under the app bar.
- **Order:** the tour (Transactions, Analysis, Categories, Settings; *Skip* ends it), then notices (update, after ADR-0060's 60 days; new bank SMS it couldn't read, with *Help* opening *Help improve the parser*), then tips: a possible duplicate in the inbox, *Sort all* once 3+ payments wait, and an *Always* offer for a merchant sorted into the same category 3+ times (ADR-0062).
- **Not nagging:** every note shows once and any button answers it; the unread-SMS notice comes back only for newer messages (by id, not counting wrong-amount reports), the update notice after its 30-day snooze. After two declined *Always* offers Purple stops offering. *Settings › Tips from Purple* turns the tour and tips off; the two notices still show. Answers stay on this phone (DataStore), not in backups.
- **Simple:** no network, no animation beyond a fade, text in one or two short sentences, TalkBack hears each note (polite live region).
- **Tests:** `PurpleTest` (order, once-only, answers), `PurpleOverlayTest`.
