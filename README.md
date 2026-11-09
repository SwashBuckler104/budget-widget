# Budget Widget

**Inbox Zero for your money.** Every bank, card and UPI SMS becomes a card in an inbox. You sort
it in one tap from a notification, a home-screen widget or the app, and you "close the day"
before midnight to keep your streak.

> **Status:** early development (milestone M0, scaffold). The app builds and installs but has no
> features yet. See [docs/SCOPE.md](docs/SCOPE.md) for the v1.0 plan.

<!-- Screenshots: added in M8 (fastlane/metadata/android/en-US/images/phoneScreenshots/). -->

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
6. **Add the widget.** Long-press an empty spot on your home screen → **Widgets** → **Budget Widget** → drag **Quick-Sort** onto the screen.
7. **Keep it alive (Xiaomi, Oppo, Vivo, Realme, Samsung).** Some phones stop background apps aggressively. In **Settings → Apps → Budget Widget → Battery**, choose **Unrestricted**. On Xiaomi, Oppo and Vivo also turn on **Autostart**. The app catches up on missed SMS anyway, but this makes notifications arrive on time.

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
