# CLAUDE.md

Budget Widget: an Android app that turns bank SMS into an inbox you sort in one tap. The spec is
**[docs/SCOPE.md](docs/SCOPE.md)** (it wins over everything else). Decisions are in
[docs/DECISIONS.md](docs/DECISIONS.md).

## Commands
```sh
./gradlew assembleDebug                                  # build
./gradlew spotlessApply                                  # format (ktlint + compose rules)
./gradlew spotlessCheck lint test assembleDebug assembleRelease   # = CI
./gradlew :parser-core:test                              # full upstream parser suite (~2.2k tests)
./gradlew :parser-core:test --tests '*SliceParserTest'   # one class (bank-coverage gate auto-skips)
scripts/check-permissions.sh app/build/outputs/apk/{debug/app-debug,release/app-release-unsigned}.apk
scripts/check-module-boundaries.sh
```
Local toolchain (no sudo): JDK 21 at `~/.local/jdks/jdk-21.0.13+9` (set via `org.gradle.java.home`
in `~/.gradle/gradle.properties`), SDK at `~/Android/Sdk` (`local.properties`). No emulator yet.

## Modules
| Module | Role |
|---|---|
| `:app` | Application, MainActivity, nav host, Hilt root, release signing |
| `:core:model` | pure Kotlin domain (JVM module) |
| `:core:database` / `:core:datastore` | Room / DataStore |
| `:core:data` | repositories, merchant-key normalizer, suggestions |
| `:core:designsystem` | theme, components; exports the Compose BOM + M3 as `api` |
| `:core:testing` | fakes, fixtures |
| `:ingest` | SMS receiver, workers, mapper. **The only module that may use `:parser-core`** |
| `:notifications`, `:widget` | notification + Glance code |
| `:feature:*` | analysis, transactions, categories, onboarding, settings (`bw.android.feature`) |
| `:parser-core` | compiles `third_party/pennywise/parser-core` (submodule) unmodified |

Convention plugins live in `build-logic/convention`: `bw.android.application|library|compose|feature`,
`bw.hilt`, `bw.jvm.library` and `bw.spotless`. SDK levels and the lint policy are in `ProjectConfig.kt`.
The package root is `budgetwidget.packageRoot` in `gradle.properties`; namespaces are derived from it.

## Hard constraints
1. No `android.permission.INTERNET`. The APK permission **allowlist** in `config/permissions-allowlist.txt` is checked in CI. Only add `tools:node="remove"` once a dependency actually brings it in.
2. Parser = PennyWise parser-core via the submodule pinned at `0623c2b`. Never edit `third_party/`. Only `:ingest` imports `com.pennywiseai.*` (enforced by Gradle plus the grep script). Expose `SmsParser` → `ParsedSms`.
3. AGPL-3.0; credit PennyWise (NOTICE, README, About); no PennyWise branding.
4. Persist SMS bodies only for financial messages.
5. Money = `Long` paise; convert from `BigDecimal` once, in the mapper, with explicit rounding.
6. No exact alarms, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` or `QUERY_ALL_PACKAGES`.
7. minSdk 26, targetSdk 36, **compileSdk 37** (ADR-0007). Latest stable libraries only.
8. No analytics, crash reporters, ads, Firebase, network libraries or AI features.

## Conventions
- Conventional Commits, small and focused; branch `m<N>-<slug>`; the user merges. No agent co-author lines.
- Warnings are errors (Kotlin, lint, ktlint); fix them rather than suppressing.
- Logic gets unit tests first (ingest, mapper, normalizer, suggestions, duplicates, streak math, DAOs).
- Never use the em dash character in any file; use "-".

## Gotchas
- **AGP 9 built-in Kotlin:** don't apply `org.jetbrains.kotlin.android`. The KGP version comes from the root `plugins { ... apply false }` classpath; AGP 9.5.1 alone would bring KGP 2.2.10.
- **`CommonExtension` has no type parameters** in AGP 9.5. The lint `htmlReport`/`xmlReport`/`sarifReport` flags are deprecated (reports are always generated).
- **Convention plugin aliases have no version** in the catalog. Adding `version = "unspecified"` breaks resolution because build-logic is already on the root classpath.
- **Compose BOM must be exported** (`api(platform(...))` in designsystem), or consumers can't resolve versionless M3/UI.
- **Lifecycle 2.9.x lint jar breaks AGP 9.5 lint.** A constraint to 2.11.0 lives in `configureAndroidCommon` (ADR-0011).
- **Upstream tests need upstream's working dir** (`SupportedBanksDocTest`, `BankSamplesDocTest`, `IosCompatibilityTest`). `:parser-core:test` sets `workingDir`. `verifySubmoduleClean` guards against writes.
- Upstream parser-core has 7 Kotlin warnings at `0623c2b` (allowed only in `:parser-core`). Draft fix: `docs/upstream/0001-*.md`.
- `libandroidx.graphics.path.so` ships pre-stripped. `keepDebugSymbols` silences AGP's "unable to strip" warning (no NDK needed).
- AGP auto-installs missing SDK packages (platform 37, build-tools 36.0.0) on first build.
- setup-gradle v6 defaults to a proprietary cache provider; we use `cache-provider: basic` (ADR-0009).
- WorkManager (M1) merges `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE`. Decide each one explicitly, and extend the allowlist in the same commit.
