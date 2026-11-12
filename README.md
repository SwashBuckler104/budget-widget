# Budget Widget

[![CI](https://github.com/SwashBuckler104/budget-widget/actions/workflows/ci.yml/badge.svg)](https://github.com/SwashBuckler104/budget-widget/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/SwashBuckler104/budget-widget)](https://github.com/SwashBuckler104/budget-widget/releases/latest)
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)

**Inbox Zero for your money.** Every bank, card and UPI SMS becomes a card in an inbox. You sort
it in one tap from a notification, a home-screen widget or the app, and you "close the day"
before midnight to keep your streak.

**[⬇ Download the latest APK](https://github.com/SwashBuckler104/budget-widget/releases/latest/download/budget-widget.apk)** - open this link on your Android phone, install, done.
The [installation guide](#installation-guide) walks through the few prompts Android shows.

> **Status:** v1.0, the MVP (milestones M0-M8 of [docs/SCOPE.md](docs/SCOPE.md)): SMS import,
> notifications with one-tap categories, the Quick-Sort and Insight widgets, the Transactions and
> Analysis tabs, onboarding, backup/restore and CSV export. New here? Jump to the
> [installation guide](#installation-guide).

## Features

- **Inbox of new payments.** Every bank SMS becomes a transaction waiting to be sorted. A notification offers your daily categories (Groceries, Restaurant, Public transport) as buttons; the **Quick-Sort widget** shows the next payment with category chips.
- **Close the day.** Sort everything before midnight to keep a streak, with an optional evening reminder.
- **Analysis.** Any week, month, year or date range: spending and income pie charts with drill-down, daily or monthly bars, the trend against the period before, and top merchants. The **Insight widget** shows today, this month and the last 7 days.
- **Transactions.** Search, filters, edit, manual cash entries, bulk actions, swipe with undo, and duplicate detection when two banks report the same payment.
- **Your data.** CSV export, JSON backup and restore, and "start fresh", all through files you choose.

## Why

- **Zero manual entry.** Transactions are read from bank SMS and parsed on the phone.
- **A habit, not a chore.** One tap to categorize, a daily deadline, a streak, and a Quick-Sort widget.
- **Private by construction.** The app has **no INTERNET permission**. Nothing leaves your phone, and you can check this yourself in *Settings → Apps → Budget Widget → Permissions*.
- **Small and simple.** No accounts, no ads, no analytics, no AI.
- **Free and open source.** For everyone, under the AGPL-3.0. Read the code, build it yourself, or help improve it.

Supported banks for v1: Kotak, Federal, Jupiter, slice, Axis, SBI, HDFC. The parser supports
many more, and any bank it recognises will work.

## Installation guide

Budget Widget is installed from GitHub Releases, not the Play Store. Setup takes about five
minutes; Android asks a few extra questions the first time because the app is sideloaded.

Below, **Settings → Apps → ...** and **Settings → Battery → ...** are in the phone's Settings app.
Every other **Settings → ...** is inside Budget Widget: tap the ⚙ gear at the top of the
**Analysis** tab.

### Before you start

- An Android phone running **Android 8.0 or newer**, with your bank's SIM in it.
- Your bank must send **SMS alerts** for transactions. The app reads SMS only: if a bank (or an
  account, such as a salary account) sends alerts by app notification or email alone, those
  transactions can't appear. Most banks let you turn SMS alerts on in their app or at a branch.
- About 30 MB of free space.

### 1. Download and install

1. On your phone, tap **[Download the latest APK](https://github.com/SwashBuckler104/budget-widget/releases/latest/download/budget-widget.apk)**. It always downloads the newest
   version. (All versions are on the [releases page](https://github.com/SwashBuckler104/budget-widget/releases).)
2. Open the downloaded file. If Android says your browser isn't allowed to install apps, tap
   **Settings**, turn on **Allow from this source**, and go back.
3. Tap **Install**. Google Play Protect may step in, because the app reads SMS and isn't from the
   Play Store:
   - *"Unsafe app blocked"* or *"App scan recommended"*: tap **More details → Install anyway**.
   - *"App blocked to protect your device"* with only an **OK** button: this is Google's fraud
     protection, active in India and some other countries. It blocks every app that can read SMS
     when installed from a browser or file manager, and it can't be skipped. Tap **OK**, then:
     1. Open the **Play Store**, tap your profile picture, then **Play Protect → ⚙ (top right)**.
     2. Turn off **Scan apps with Play Protect** and confirm.
     3. Open the downloaded `budget-widget.apk` again (Files app → **Downloads**) and install it.
     4. Go back to Play Protect settings and turn scanning **on** again. The app stays installed.
4. Tap **Open**.

The APK is about 4 MB, so a download that stops at 3.7 MB has finished.

### 2. First launch

A short setup walks you through seven steps (tap **Next** to move on):

1. **Welcome.** What the app does, and that nothing leaves your phone.
2. **Reading bank SMS.** Tap **Allow SMS access**. On Android 13 and newer, Android blocks SMS
   access for sideloaded apps at first: you see *"Android blocked the request"* or a greyed-out
   *"Restricted setting"* switch. To unlock it:
   1. Open **Settings → Apps → Budget Widget**.
   2. Tap **⋮** (top right) → **Allow restricted settings**, and confirm with your PIN or
      fingerprint. (If the option isn't there yet, tap **Allow SMS access** once more so Android
      registers the block, then come back.)
   3. Go to **Permissions → SMS → Allow**, and return to the app.
3. **Notifications.** Tap **Allow notifications**. New transactions arrive as notifications with
   category buttons.
4. **Battery.** Tips for instant alerts; see step 3 below.
5. **Where to start.** Import the last 30 or 90 days of bank SMS from your inbox, or
   **Start fresh** to track only payments from now on. You can import older SMS later from
   **Settings → Import older SMS**.
6. **Your categories.** A starter set with subcategories. Rename, add, reorder or archive them any
   time in the **Categories** tab.
7. **Widget.** Tap **Add the widget** to put the Quick-Sort widget on your home screen, then
   **Start**.

### 3. Keep notifications on time (important on Samsung, Xiaomi, Oppo, Vivo, Realme)

Many phones put background apps to sleep, which delays or drops notifications. The app catches up
on missed SMS every few hours anyway, but to get them instantly:

- **All phones:** **Settings → Apps → Budget Widget → Battery → Unrestricted**.
- **Samsung:** also open **Settings → Battery → Background usage limits** and make sure Budget
  Widget is not under *Sleeping apps* or *Deep sleeping apps*.
- **Xiaomi, Oppo, Vivo, Realme:** also turn on **Autostart** for Budget Widget (in the app's
  settings page or the phone's security app).

### 4. Add widgets

Long-press an empty spot on your home screen → **Widgets** → **Budget Widget**, then drag one onto
the screen:

- **Quick-Sort** (4x2 by default, resizable): the next payment to sort with one-tap category chips.
- **Insight** (4x2 or larger): today, this month and the last 7 days; larger sizes add your top
  categories.

### 5. Start your budget

- Already imported history but want the budget to begin today? **Settings → Start fresh** deletes
  all transactions and tracks from now on. Categories, merchant rules and settings stay (take a
  backup first if you might want the history back).
- Make one small payment. Within seconds you should get a notification: tap a category and you're
  done. Sort everything before midnight to keep your streak.

### Updating

Tap the same [download link](https://github.com/SwashBuckler104/budget-widget/releases/latest/download/budget-widget.apk) and install the APK over the old one. Your data stays. If Play Protect blocks the
update, use the same steps as in [Download and install](#1-download-and-install).
[Obtainium](https://github.com/ImranR98/Obtainium) can watch this repository and update the app for
you.

### Back up, move phones, reinstall

- **Settings → Back up** saves everything (transactions, categories, merchant rules, settings) to a
  file you choose. The file isn't encrypted, so keep it somewhere safe. **Settings → Restore**
  loads it on any phone, replacing what's there. **Settings → Export to CSV** gives a spreadsheet.
- Uninstalling deletes all app data, and Android's cloud backup is turned off for this app. Back up
  first.
- *"App not installed"* or *"package conflicts"* when updating means the installed copy was signed
  with a different key (for example a build made from source). Back up, uninstall, install the
  release APK, then restore.

### Troubleshooting

| Problem | What to do |
|---|---|
| *"App blocked to protect your device"* when installing | Turn off Play Protect scanning while you install; see step 1. |
| A payment didn't show up | Check that the bank sent an SMS at all. Then look in **Settings → Unparsed bank SMS**: messages the parser couldn't read, and alerts from banks it doesn't know yet, land there. |
| Notifications arrive late or not at all | Do step 3 above. In the app, check **Settings → Transaction notifications** and the minimum amount; also check the phone's notification settings for Budget Widget. |
| SMS permission switch is greyed out | Follow the *Allow restricted settings* steps in step 2. |
| Widget shows old data | Open the app once; widgets refresh on every change. If it persists, remove and re-add the widget. |
| A bank you use isn't recognised | Copy the message from **Unparsed bank SMS**, remove your name and account number, and open a [bank format issue](https://github.com/SwashBuckler104/budget-widget/issues/new/choose). |

**Verifying a download (optional):** every APK on a release has a `.sha256` file next to it.
Compare it with `sha256sum budget-widget.apk`.

## Build from source

Requirements: JDK 21, an Android SDK (platform 37 and build-tools; the Gradle plugin downloads
missing pieces), and git.

```sh
git clone --recurse-submodules https://github.com/SwashBuckler104/budget-widget.git
cd budget-widget
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleDebug                                # app/build/outputs/apk/debug/app-debug.apk
./gradlew spotlessCheck lint test assembleRelease      # what CI runs
```

The baseline profile in `app/src/release/generated/` is regenerated on an emulator; see
[baselineprofile/README.md](baselineprofile/README.md).

See [CONTRIBUTING.md](CONTRIBUTING.md) for the module layout and conventions.

## Contributing

Contributions are welcome, especially SMS formats from banks the parser doesn't recognise yet.
Use the [issue templates](https://github.com/SwashBuckler104/budget-widget/issues/new/choose) to
report a bug or an SMS format, and read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull
request. Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md). Report security
or privacy problems privately, as described in [SECURITY.md](SECURITY.md).

## Privacy

See [PRIVACY.md](PRIVACY.md). In short: the app reads SMS on the phone, keeps only financial
messages, and has no way to send anything anywhere.

## Credits

SMS parsing uses **PennyWise parser-core** by the PennyWise authors
(<https://github.com/sarim2000/pennywiseai-tracker>, AGPL-3.0). It is included unmodified as a git
submodule. Budget Widget is an independent project and is not affiliated with or endorsed by
PennyWise. See [NOTICE](NOTICE).

## License

[GNU Affero General Public License v3.0](LICENSE). If you distribute a modified version, you must
also make its source code available under the same license.
