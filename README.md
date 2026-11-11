# Budget Widget

**Inbox Zero for your money.** Every bank, card and UPI SMS becomes a card in an inbox. You sort
it in one tap from a notification, a home-screen widget or the app, and you "close the day"
before midnight to keep your streak.

> **Status:** v1.0 feature complete (milestones M0-M8 of [docs/SCOPE.md](docs/SCOPE.md)): SMS
> import, notifications with one-tap categories, the Quick-Sort and Insight widgets, the
> Transactions and Analysis tabs, onboarding, backup/restore and CSV export.

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

Supported banks for v1: Kotak, Federal, Jupiter, slice, Axis, SBI, HDFC. The parser supports
many more, and any bank it recognises will work.

## Install (for family)

Budget Widget is installed from GitHub Releases, not the Play Store, so Android asks a few extra
questions the first time.

1. **Download.** On your phone, open the [latest release](https://github.com/SwashBuckler104/budget-widget/releases/latest) and tap `budget-widget-vX.Y.Z.apk`.
2. **Allow the install.** If Android says your browser isn't allowed to install apps, tap **Settings**, turn on **Allow from this source**, then go back.
3. **Play Protect.** If you see *"Unsafe app blocked"* or *"App scan recommended"*, tap **More details → Install anyway**. This shows up because the app isn't from the Play Store. It isn't a virus warning about this app.
4. **Allow SMS ("restricted settings").** Open the app and allow SMS when asked. On Android 13 and newer, SMS access for sideloaded apps is blocked at first, and the switch may be greyed out with a *"Restricted setting"* message. To unlock it:
   1. Open **Settings → Apps → Budget Widget**.
   2. Tap the **⋮** menu (top right) → **Allow restricted settings**, and confirm with your PIN or fingerprint.
   3. Go to **Permissions → SMS → Allow**.
5. **Allow notifications** when asked. That's how new transactions reach you.
6. **Add the widget.** The last setup step offers to add it. Or long-press an empty spot on your home screen → **Widgets** → **Budget Widget** → drag **Quick-Sort** (or **Insight**) onto the screen.
7. **Keep it alive (Xiaomi, Oppo, Vivo, Realme, Samsung).** Some phones stop background apps aggressively. In **Settings → Apps → Budget Widget → Battery**, choose **Unrestricted**. On Samsung, also check **Settings → Battery → Background usage limits** and make sure Budget Widget is not under *Sleeping* or *Deep sleeping apps*. On Xiaomi, Oppo and Vivo turn on **Autostart**. The app catches up on missed SMS every few hours anyway, but this makes notifications arrive on time.

**A bank's SMS don't show up?** Look in **Settings → Unparsed bank SMS**. Messages from a supported
bank that couldn't be read, and transaction alerts from banks the parser doesn't know yet, are kept
there. Copy one (remove your name and account number first) into a
[bank format issue](https://github.com/SwashBuckler104/budget-widget/issues/new/choose).

**Updating:** download the new APK and install it over the old one. Your data stays. You can also
use [Obtainium](https://github.com/ImranR98/Obtainium) with this repository's URL to get updates
automatically.

**Verifying a download (optional):** every release has a `.sha256` file next to the APK. Compare
it with `sha256sum budget-widget-vX.Y.Z.apk`.

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
