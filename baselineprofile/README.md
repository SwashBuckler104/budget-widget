# Baseline profile

`app/src/release/generated/baselineProfiles/` holds the checked-in profile. Regenerate it after
changes to start-up or the main screens:

```sh
scripts/capped.sh emulator -avd bw36 -no-window &   # an API 28+ emulator
ANDROID_SERIAL=emulator-5554 scripts/capped.sh ./gradlew :app:generateBaselineProfile
```

**Unplug your own phone first, or pin `ANDROID_SERIAL` as above.** Generation runs on every
connected device and reinstalls the app there, which deletes the app's data on that device.

The generator (`BaselineProfileGenerator`) starts the app from a fresh install, walks through setup
without granting permissions, and opens every tab.
