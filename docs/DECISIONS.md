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
