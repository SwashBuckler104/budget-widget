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
