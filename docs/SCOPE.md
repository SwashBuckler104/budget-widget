# Budget Widget — Product & Technical Scope (v1.0)

> **Positioning:** *Inbox Zero for your money.* Every bank SMS becomes a card in an inbox. You sort it in one tap — from a notification, a home-screen widget, or the app — and you "close the day" before midnight.
>
> **Audience (v1):** the developer and family (≈2–6 Android phones), installed from GitHub Releases. Built Play-policy-compliant from day one so Play Store remains an option later.
>
> **Status:** Phase 2 output — final scope for implementation. Date: 2026-11-08.

---

## 1. Goals and non-goals

### Goals
1. **Zero manual entry** for bank / card / UPI transactions — read from SMS, parsed on-device.
2. **Keep the habit alive** — categorizing is a one-tap, low-friction ritual with a daily "close the day" deadline, a streak, and a home-screen widget that works even when you were too busy for the notification.
3. **Wallet-app-grade quality** — Material 3, smooth, fast, accessible, dark mode, no jank.
4. **Private by construction** — the app ships **without the `INTERNET` permission**. All data stays on the phone.
5. **Small and simple** — target APK < 10 MB. No AI models, no accounts, no ads, no paywall.

### Non-goals (v1)
- Cloud sync, multi-device household view, bank account linking (Account Aggregator).
- On-device AI / chat, PDF statement import.
- Multi-currency (INR only in v1; the parser supports more, we ignore non-INR for now).
- Investments tracking, bill reminders, loans, subscriptions management.
- iOS.

---

## 2. Key decisions (locked)

| Decision | Choice | Why |
|---|---|---|
| SMS parsing | **Reuse PennyWise `parser-core`** (AGPL-3.0) via git submodule, pinned commit | 190+ bank parsers incl. all of ours; pure Kotlin, no Android deps; has tests |
| License | **AGPL-3.0** for our repo | Required by reusing AGPL code. Credit PennyWise in README + NOTICE. Don't use the "PennyWise" name/branding (see their TRADEMARKS.md) |
| Platform | Native Android, Kotlin, Jetpack Compose | Quality-grade UI, Glance widgets |
| minSdk / targetSdk | **26 (Android 8.0) / 36 (Android 16)** | Matches parser-core; covers family phones |
| Distribution | GitHub Releases (signed APK), Obtainium-friendly; F-Droid/Play later | Family-first; permissions granted manually |
| Network | **No `INTERNET` permission** — enforced in CI | Strongest privacy guarantee for an SMS-reading app |
| Money type | `Long` paise everywhere in our code | No floating-point errors |

### Verified parser coverage for our banks
`KotakBankParser`, `FederalBankParser`, `JupiterBankParser`, `SliceParser`, `AxisBankParser`, `SBIBankParser`, `HDFCBankParser` — all present in upstream `parser-core` with unit tests (checked against upstream `main` @ `0623c2b`, 2026-11-08).

**parser-core facts (from source):**
- Module is Kotlin Multiplatform (`jvm()` + iOS targets). Code lives in `src/main/kotlin` and `src/commonMain/kotlin`. Only external deps: `java.math`, `java.text`, `java.util`, `kotlinx-datetime 0.7.1`. **No Android imports.**
- Entry point: `BankParserFactory.parse(smsBody, sender, timestampMillis): ParsedTransaction?`, plus `isKnownBankSender(sender)`, `getParser(sender)`.
- `ParsedTransaction` fields: `amount: BigDecimal`, `type: TransactionType`, `merchant`, `reference`, `accountLast4`, `balance`, `creditLimit`, `smsBody`, `sender`, `timestamp`, `bankName`, `isFromCard`, `currency`, `fromAccount`, `toAccount`, … and `generateTransactionId()` = md5(sender | amount | md5(body)[0..16]) — stable for dedupe across receiver vs inbox-scan.
- `TransactionType`: `INCOME, EXPENSE, CREDIT, TRANSFER, INVESTMENT, BALANCE_UPDATE`.

---

## 3. Core concepts

- **Transaction** — one money movement. Source = `SMS` or `MANUAL`.
- **Status** — `PENDING` (needs sorting) → `CATEGORIZED` or `IGNORED`.
- **Overdue** — derived, not stored: `status == PENDING && occurredAt.localDate < today`.
- **Inbox** — all `PENDING` transactions, oldest first. Overdue items are flagged.
- **Close the day** — at local midnight, if a day had zero pending transactions, it counts as "closed". Consecutive closed days = **streak**. Nothing is deleted or auto-sorted at midnight.
- **Merchant memory** — a rule `merchantKey → category` learned from your taps; drives suggestions and (optionally) auto-sorting.

---

## 4. Features (v1.0 MVP)

### F1. Onboarding and permissions
1. Welcome → what the app does, privacy promise ("no internet permission — verify it in App info").
2. **Prominent disclosure** screen for SMS (required by Play policy, good practice anyway): we read only bank/financial SMS, parse on device, store only financial messages.
3. Request `RECEIVE_SMS`, `READ_SMS`, `POST_NOTIFICATIONS` (API 33+).
4. **Restricted settings guide** (sideloaded installs on Android 13+/15+): detect denial → show illustrated steps + button to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` ("⋮ → Allow restricted settings → Permissions → SMS → Allow").
5. Optional: battery optimization guide (open `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`; **do not** declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). Show OEM-specific tips (Xiaomi/Oppo/Vivo/Samsung autostart).
6. **Backfill choice:** last 30 days / 90 days (default) / start fresh. Backfilled items land in the inbox as PENDING but are **not** counted as overdue for streaks and get a "Sort all as…" bulk action.
7. Category seed set preview (editable later).
8. Prompt to add the Quick-Sort widget (use `AppWidgetManager.requestPinAppWidget` where supported).

### F2. SMS ingestion (the reliability core)
- **Real-time path:** manifest `BroadcastReceiver` for `android.provider.Telephony.SMS_RECEIVED` (exempt from implicit-broadcast limits). Extract PDUs via `Telephony.Sms.Intents.getMessagesFromIntent`, **concatenate multipart bodies** per sender, then hand off to an **expedited `OneTimeWorkRequest`** (fallback to normal if quota exhausted). Do not do DB work on the main thread; use `goAsync()` only for the hand-off.
- **Safety-net path:** `ReconcileWorker` (periodic, every 3 h, plus on app start and after onboarding) queries `content://sms/inbox` where `date > checkpoint - 10 min overlap`, sorted ascending, and runs the same pipeline. Checkpoint in DataStore.
- **Pipeline (single function, idempotent):**
  1. `isKnownBankSender(sender)` → if false, drop (never persist non-financial SMS).
  2. `BankParserFactory.parse(body, sender, ts)` → if null, record sender + body hash into a local **"Unparsed bank SMS"** list (body kept only if sender is a known bank sender; user can view/copy/delete for parser bug reports). Cap at 200 entries.
  3. Map `ParsedTransaction` → our `Transaction` (see mapping table below).
  4. Upsert by unique `txnHash` (= `generateTransactionId()`); duplicates are no-ops.
  5. Cross-source duplicate check (see below).
  6. Apply merchant rule (suggest or auto-sort).
  7. Post notification (if enabled and status PENDING), refresh widgets (debounced).
- **Type mapping:**

| parser `TransactionType` | Our direction | Default status / category |
|---|---|---|
| `EXPENSE` | OUT | PENDING |
| `CREDIT` (credit-card spend) | OUT, `isCard = true` | PENDING |
| `INCOME` | IN | PENDING (income categories) |
| `TRANSFER` | OUT or IN (from `fromAccount/toAccount`, else OUT) | PENDING, suggest "Transfers" |
| `INVESTMENT` | OUT | PENDING, suggest "Investments" |
| `BALANCE_UPDATE` | — | **Not a transaction.** Update account balance only |
| non-INR currency | — | Skip in v1 (log to Unparsed list as "unsupported currency") |

- **Cross-source duplicates** (critical for Jupiter↔Federal and Slice/UPI apps): when a new transaction has the same amount, opposite-free same direction, `|Δt| ≤ 10 min`, and a different `bankName`, link it via `duplicateOfId` and show it in the inbox as **"Looks like the same payment as ₹X at HH:MM — Merge / Keep both"**. Merge = mark newer as IGNORED with reason DUPLICATE. Never auto-merge in v1.
- **Accounts:** auto-create `Account(bankName, last4)` on first sight; user can nickname/hide.

### F3. Transaction notification (instant ask)
- Channel **"New transactions"** (default importance HIGH, user-tunable). One notification per pending transaction, grouped with a summary when ≥ 2.
- Content: title `₹450 · SWIGGY`, text `HDFC ••1234 · 8:42 PM`, red/green accent by direction.
- **Actions (Android max 3):** top 3 suggested categories (leaf names, e.g. "Food delivery"). If fewer than 3 suggestions → fill with "Ignore".
- Tap body → **Quick-Sort sheet** (transparent `Activity` hosting a Compose `ModalBottomSheet`).
- Action → `BroadcastReceiver` → update DB → cancel notification → refresh widgets. Must work with the app process cold.
- Setting: notifications on/off; "only for amounts above ₹X" (default 0).

### F4. Quick-Sort widget (the hero)
Built with **Jetpack Glance** (`GlanceAppWidget`, `GlanceTheme` with dynamic colors).
- **Sizes (responsive `SizeMode.Responsive`):**
  - **2×2 compact:** pending count, "Sorted ✓" or "3 to sort", tap → Quick-Sort sheet.
  - **4×2 standard:** header `3 to sort · closes at midnight` (in the last 3 h: `~2 h left`); card for the oldest pending transaction (amount, merchant, account, time; overdue badge); **row of up to 6 category chips** (icon + short label) from suggestions; footer `‹  Ignore  More  ›`.
  - **4×3+ large:** same plus a mini list of the next 2 pending items.
- Chip tap → `ActionCallback` writes category → advances to next item → if inbox empty shows **empty state:** `All sorted ✓ · Today ₹1,240 · 🔥 12-day streak`.
- "More" → Quick-Sort sheet (full category grid with subcategories, note field, "remember for this merchant" toggle).
- **Constraints to respect:** Glance cannot run a live countdown (use coarse text updated by workers), widget updates are rate-limited (debounce `updateAll` ~500 ms), each widget instance stores its "current index" in Glance state, handle 0 pending / 100+ pending / long merchant names / RTL / large font.

### F5. Insight widget (glanceable visualization)
- Resizable 4×2 → 4×4.
- Content: **Today spent**, **This month spent** vs same day last month (▲/▼ %), **7-day bar sparkline**, **top 3 categories** with proportional bars.
- Charts rendered to a `Bitmap` by `InsightRenderWorker` (Android `Canvas`, theme-aware light/dark, uses dynamic color tokens resolved at render time) → shown via Glance `Image`. Re-render on data change (debounced 2 s) and at 00:05 daily.
- Tap → app Analysis tab.

### F6. Close-the-day ritual
- **Evening nudge** at 21:00 (configurable, or off): "3 transactions left to sort today" with action "Sort now". Scheduled with WorkManager (`PeriodicWorkRequest` 24 h + computed initial delay) or inexact `AlarmManager.setWindow`. **Do not** use exact alarms (`SCHEDULE_EXACT_ALARM` is restricted).
- **Midnight rollover worker** (~00:05): write `DayClose(date, pendingAtMidnight)` for yesterday, recompute streak, refresh widgets.
- Streak shown on Quick-Sort empty state and Analysis header. Backfilled items never break a streak.

### F7. App — three tabs (Material 3, bottom navigation)

**Tab 1 — Analysis**
- Month switcher (swipe/arrow), account filter.
- Summary cards: Spent, Income, Net, Avg/day.
- Category donut → tap slice → subcategory breakdown → tap → filtered transaction list.
- Daily spend bar chart for the month; cumulative line vs previous month.
- Top merchants (top 5).
- Inbox banner at top if pending > 0 ("5 to sort").

**Tab 2 — Transactions** (view + edit)
- **Inbox section pinned at top** (pending + overdue, with duplicate suggestions), then all transactions grouped by day with day totals.
- Search (merchant, note, amount), filter chips (category, account, direction, status, date range).
- **Edit sheet:** category/subcategory picker, note, direction/type toggle (Expense / Income / Transfer), merchant display name (with "apply to all from this merchant" → updates rule + past items optionally), Ignore/Un-ignore, view original SMS (expandable), amount editable only for manual entries (SMS amounts read-only, with "Report wrong amount" → adds to Unparsed list).
- FAB → **Add manual transaction** (cash): amount keypad, direction, category, date/time, note.
- Multi-select → bulk categorize / ignore.
- Swipe actions: right = categorize (opens picker), left = ignore. Undo snackbar for every destructive action.

**Tab 3 — Categories** (+ sub-categories)
- List of categories (icon, color, kind: Expense/Income/Transfer), expandable to subcategories.
- Add / edit / reorder (drag) / archive (archived categories hidden from pickers but kept for history). No hard delete if referenced.
- Per-category: learned merchant rules (view, delete, move).
- Two levels max (category → subcategory). Transactions point at the **leaf** chosen (a category with no subcategories is itself a leaf).

### F8. Merchant memory and suggestions
- `merchantKey` = normalize(merchant): lowercase, trim, strip UPI handles/`@ybl`-style suffixes, reference numbers, digits runs ≥ 4, punctuation; collapse spaces.
- On every user categorization: upsert `MerchantRule(merchantKey → categoryId, confirmCount++, lastUsedAt)`; if user picks a different category, reset `confirmCount = 1` with the new category.
- **Suggestion ranking** (for chips/actions): (1) rule category, (2) categories used for this merchantKey historically, (3) most-used categories in last 60 days for the same direction and similar amount band (±50%), (4) global most-used, (5) seed defaults. Deduplicate; return top 6.
- **Auto-sort (setting, default OFF):** "Auto-sort merchants I've confirmed 3+ times". Auto-sorted items are `CATEGORIZED` with `autoCategorized = true`, shown with a subtle "auto" tag and appear in a "Review auto-sorted" filter. Rationale: the product's value is awareness — don't silently remove it by default.

### F9. Settings
- Notifications (on/off, threshold), evening nudge time, auto-sort toggle.
- Accounts (nickname, hide).
- Re-run backfill (choose range).
- **Unparsed bank SMS** list (copy text to report a parser bug upstream; delete).
- **Export CSV** and **Backup / Restore** (JSON, via Storage Access Framework — no network). Backup includes categories, rules, transactions, settings; versioned schema.
- Theme (System / Light / Dark), dynamic color on/off.
- About: version, license (AGPL-3.0), open-source credits (PennyWise parser-core), privacy statement, "No internet permission" badge.

### Seed categories (editable)
Chosen by the owner on 2026-11-09 (replaces the original list; docs/DECISIONS.md ADR-0018).
- **Food & Drinks:** Groceries; Restaurant, Fast-food; Drinks, Alcohol
- **Shopping:** Clothes, Shoes & Jewelry; Drug-store, Chemist; Electronics, Accessories; Gifts, Joy; Health & Beauty; Home, Garden; Kids; Pets, Animals; Stationery, Tools
- **Housing:** Electricity, Utilities; Maintenance, Repairs; Rent
- **Transportation:** Trips; Flight; Public transport
- **Vehicle:** Fuel; Vehicle insurance; Vehicle maintenance; Vehicle accessories
- **Life & Entertainment:** Gym, Fitness; Books, Audio, Subscription; Sports; Education, Development; TV, Streaming; AI Subscription; Wellness, Beauty
- **Communication, PC:** Internet; Phone, Cell phone; Software, Apps, Games
- **Financial Expenses:** Fines; Insurances; Loans, Interest; Taxes; Self Transfer (outgoing leg)
- **Investments** (transfer type): Financial investments (SIP, bonds, etc.); Realty; Savings (emergency fund)
- **Income:** Salary; Freelancing; Other; Self Transfer (incoming leg)

Users can add their own main categories and subcategories.

---

## 5. Architecture

### Module layout (Gradle, version catalog, convention plugins in `build-logic/`)
```
:app                       — Application, MainActivity, nav host, DI root, QuickSortActivity
:core:model                — pure Kotlin domain models (Transaction, Category, Money…)
:core:database             — Room DB, entities, DAOs, migrations, schema export
:core:data                 — repositories, merchant-key normalizer, suggestion engine, pipeline
:core:datastore            — DataStore prefs (settings, checkpoint)
:core:designsystem         — theme, typography, icons, shared components, category icon set
:core:testing              — fakes, test fixtures, anonymized SMS corpus
:ingest                    — SmsReceiver, ReconcileWorker, ParsedTransaction → domain mapper
:notifications             — channels, builders, action receiver
:widget                    — Glance widgets (QuickSort, Insight), ActionCallbacks, InsightRenderWorker
:feature:analysis
:feature:transactions
:feature:categories
:feature:onboarding
:feature:settings
:parser-core               — thin Gradle module compiling the PennyWise submodule sources (JVM)
third_party/pennywise      — git submodule (pinned commit), NOT modified
```

### Parser integration
- `git submodule add https://github.com/sarim2000/pennywiseai-tracker third_party/pennywise` → pin to a reviewed commit.
- `:parser-core/build.gradle.kts` = `kotlin("jvm")` (JVM 17 target is fine for Android) with
  `sourceSets.main.kotlin.srcDirs("../third_party/pennywise/parser-core/src/main/kotlin", "../third_party/pennywise/parser-core/src/commonMain/kotlin")`, test srcDirs + resources from upstream `src/test`, dependency `org.jetbrains.kotlinx:kotlinx-datetime:0.7.1`. If any upstream test file needs JUnit 5, wire JUnit Platform for this module only.
- **Our code never imports parser classes outside `:ingest`.** `:ingest` exposes `SmsParser` interface → `ParsedSms` (our type). This anti-corruption layer lets us swap/upgrade the parser safely.
- **Our own golden tests:** `:ingest` contains anonymized real SMS from family phones (one file per bank: Kotak, Federal, Jupiter, Slice, Axis, SBI, HDFC) with expected amount/direction/merchant/last4. Upgrading the submodule must keep these green.
- Upgrade procedure documented in `docs/PARSER_UPGRADE.md` (bump submodule → run upstream tests + golden tests → review diff of bank parsers we use).

### Tech stack (use latest **stable** versions at implementation time; verify each)
Kotlin 2.x + KSP · Jetpack Compose (BOM) · Material 3 · Navigation Compose (type-safe routes) · Hilt · Room · WorkManager (+ Hilt Work) · Jetpack Glance (`glance-appwidget`, `glance-material3`) · DataStore · kotlinx-coroutines / Flow · kotlinx-datetime · kotlinx-serialization (backup JSON) · Vico (in-app charts, Apache-2.0) or custom Canvas · Baseline Profiles · R8 full mode.
Testing: JUnit, Turbine, Truth/AssertK, Robolectric (receivers/workers), Room migration tests, Compose UI tests, Glance `glance-testing`.
Quality: Spotless + ktlint, Android Lint (warnings as errors for new code), Detekt (optional).

### Data model (Room)
```
account(id PK, bankName, last4, nickname?, kind[BANK|CARD|WALLET], lastBalancePaise?, hidden, createdAt)
category(id PK, parentId? FK→category, name, iconKey, colorArgb, kind[EXPENSE|INCOME|TRANSFER], sortOrder, archived, isSeed)
transaction(
  id PK, txnHash UNIQUE?, source[SMS|MANUAL], amountPaise, direction[OUT|IN], parserType?,
  merchantRaw?, merchantDisplay?, merchantKey?, accountId? FK, occurredAt (epoch ms), 
  smsSender?, smsBody?, reference?, balanceAfterPaise?, isCard,
  status[PENDING|CATEGORIZED|IGNORED], ignoreReason?[USER|DUPLICATE], categoryId? FK (leaf),
  autoCategorized, isBackfill, note?, duplicateOfId? FK, editedByUser, createdAt, updatedAt)
  INDEX(status, occurredAt), INDEX(categoryId, occurredAt), INDEX(merchantKey)
merchant_rule(merchantKey PK, categoryId FK, confirmCount, lastUsedAt)
day_close(date PK 'YYYY-MM-DD', pendingAtMidnight, closed)
unparsed_sms(id PK, sender, bodyHash UNIQUE, body?, receivedAt, reason)
```
Export Room schemas to `core/database/schemas/` and write a migration test for every version bump.

### Threading and flow
`SmsReceiver` → `IngestWorker(expedited)` → `IngestPipeline` (Dispatchers.IO, single `Mutex` to serialize) → Room → `Flow`s → ViewModels/StateFlow → Compose. Widgets refresh via a `WidgetRefresher` (debounced `GlanceAppWidget.updateAll`). Notification actions and widget callbacks call the same `CategorizeUseCase`.

---

## 6. Non-functional requirements
| Area | Target |
|---|---|
| Cold start | < 500 ms to first frame on a mid-range device (baseline profile) |
| SMS → stored | < 300 ms; notification within 2 s of SMS arrival |
| Widget tap → updated | < 1 s perceived |
| APK size | < 10 MB (single universal APK, R8) |
| Network | **Zero.** CI fails if merged manifest contains `android.permission.INTERNET` |
| Accessibility | TalkBack labels on all chips/cards, 48 dp touch targets, supports 200% font scale, color never the only signal (± sign, icons) |
| Localization | All strings in resources; English v1; Indian number formatting (₹1,23,456) |
| Compatibility | Android 8–16; dual-SIM; RTL-safe layout |
| Reliability | No lost transactions across reboot, Doze, app kill (reconcile worker proves it) |

---

## 7. Privacy and Play-policy readiness
- Declared permissions limited to: `RECEIVE_SMS`, `READ_SMS`, `POST_NOTIFICATIONS`, `VIBRATE` (if used). No `INTERNET`, no `RECEIVE_BOOT_COMPLETED` (WorkManager handles reboot), no exact alarms, no `QUERY_ALL_PACKAGES`.
- Persist SMS bodies **only** for parsed financial messages (and known-bank unparsed ones, capped, user-deletable). Never persist personal SMS.
- In-app privacy statement + `PRIVACY.md`. Prominent disclosure before the permission prompt.
- Backups are plain JSON → warn the user to store them safely (encryption in v1.1).

---

## 8. Distribution
- GitHub Actions: on tag `v*` → build release APK signed with keystore from repo secrets → create GitHub Release with changelog + SHA-256.
- README: install guide for family — download, Play Protect warning ("More details → Install anyway"), "Allow restricted settings", grant SMS, add widget. Include screenshots.
- `fastlane/metadata/android/en-US/` structure from day one (F-Droid/Obtainium/IzzyOnDroid friendly).
- Note: Android developer verification goes global in 2027 — register as a developer before then to keep sideloaded installs working on certified devices.

---

## 9. Risks and critique (and mitigations)
| Risk | Impact | Mitigation |
|---|---|---|
| Upstream parser changes break us | Wrong amounts | Pinned submodule, adapter layer, our golden SMS tests gate upgrades |
| AGPL obligations / trademark | Legal | Repo is AGPL-3.0, NOTICE + README credit, no PennyWise branding |
| OEM battery killers drop broadcasts | Missed transactions | ReconcileWorker inbox scan + checkpoint overlap; OEM guide in onboarding |
| Same payment, two SMS (Jupiter + Federal, Slice + bank, card + UPI app) | Double counting | Cross-source duplicate suggestion with Merge; never auto-merge in v1 |
| Notification fatigue | Users disable everything | Grouping, threshold, widget as calm alternative, evening nudge only once |
| Auto-sort removes the mindful moment | Habit loss | Auto-sort OFF by default; "Review auto-sorted" filter |
| Glance limits (no live timers, update throttling, no custom drawing) | Widget UX gaps | Coarse time text, debounced updates, bitmap-rendered charts |
| Sideload friction (Play Protect, restricted settings, 2027 verification) | Family can't install | Illustrated install guide; register developer identity; Play route kept open |
| Parser misses a family bank format | Missing data | Unparsed SMS list → add golden test → contribute fix upstream |
| Backup file leakage | Privacy | Warning now; encrypted backup in v1.1 |

---

## 10. Milestones and acceptance criteria
| # | Milestone | Done when |
|---|---|---|
| M0 | Scaffold | Multi-module project builds; CI (build, unit tests, lint, spotless, no-INTERNET check) green; submodule + `:parser-core` compile; upstream parser tests for our 7 banks pass in our build |
| M1 | Ingestion + data | Receiver + IngestWorker + ReconcileWorker + backfill store real SMS idempotently; golden tests for 7 banks green; debug screen lists transactions |
| M2 | Categories + Quick-Sort sheet | Seed categories; Categories tab CRUD/reorder/archive; Quick-Sort sheet categorizes; merchant rules + suggestion engine unit-tested |
| M3 | Notifications | Per-transaction notification with 3 suggestion actions working from cold process; grouping; settings toggle |
| M4 | Quick-Sort widget + day close | 2×2/4×2/4×3 widget sorts items via chips without opening app; empty state; evening nudge; midnight rollover + streak |
| M5 | Transactions tab | Inbox pinned, list, search, filters, edit sheet, manual add, bulk actions, swipe + undo, duplicate merge |
| M6 | Analysis tab | Month summary, donut drill-down, daily bars, top merchants, previous-month comparison |
| M7 | Insight widget | Bitmap-rendered charts, light/dark, debounced refresh |
| M8 | Polish + release | Onboarding (disclosure, restricted settings guide, backfill), settings, CSV export, backup/restore, a11y pass, baseline profile, APK < 10 MB, signed GitHub Release v1.0.0 |

### v1.1 backlog
Category budgets with widget progress ring · split transaction · biometric app lock · encrypted backup · Hindi localization · recurring-payment detection · household merge via backup import · Play Store submission.