# Baseline profile

`app/src/release/generated/baselineProfiles/` holds the checked-in profile. Regenerate it after
changes to start-up or the main screens:

```sh
scripts/capped.sh emulator -avd bw36 -no-window &   # API 28+ emulator (or a phone over USB)
scripts/capped.sh ./gradlew :app:generateBaselineProfile
```

The generator (`BaselineProfileGenerator`) starts the app from a fresh install, walks through setup
without granting permissions, and opens every tab.
