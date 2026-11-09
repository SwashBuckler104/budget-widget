# Contributing

Thanks for helping. Budget Widget is a small, privacy-first app, so most contributions are bank
SMS formats, bug fixes and polish. Read [docs/SCOPE.md](docs/SCOPE.md) before proposing features:
v1 scope is fixed.

## Setup

- JDK 21, an Android SDK (platform 37), git.
- `git clone --recurse-submodules ...`, or run `git submodule update --init` after cloning.
- `local.properties` with `sdk.dir=...`, or `ANDROID_HOME`.

```sh
./gradlew spotlessApply                                 # format
./gradlew spotlessCheck lint test assembleRelease       # everything CI runs (plus the scripts below)
scripts/check-permissions.sh app/build/outputs/apk/release/app-release-unsigned.apk
scripts/check-module-boundaries.sh
```

## Ground rules

- **No network.** No `INTERNET` permission, and no analytics, crash reporting, ads, Firebase or network libraries. CI fails on any permission not listed in `config/permissions-allowlist.txt`.
- **Money is `Long` paise.** Never `Double`/`Float`.
- **Only `:ingest` touches the parser** (`com.pennywiseai.*`). Everything else uses our own `SmsParser` / `ParsedSms` types.
- **Never edit `third_party/`.** Parser fixes go to the upstream project; see [docs/PARSER_UPGRADE.md](docs/PARSER_UPGRADE.md).
- **Store only financial SMS.**
- Warnings are errors: Kotlin, lint and ktlint.

## Workflow

- Branch from `main`: `m<N>-<slug>` for milestones, `fix/<slug>` or `feat/<slug>` otherwise.
- [Conventional Commits](https://www.conventionalcommits.org/): `feat(widget): ...`, `fix(ingest): ...`, `test: ...`, `build: ...`, `ci: ...`, `docs: ...`. One logical change per commit, and the message says what changed and why.
- Logic gets unit tests before UI depends on it.
- Decisions the spec doesn't cover go in [docs/DECISIONS.md](docs/DECISIONS.md).

## Reporting a bank SMS the app doesn't understand

Use the **Bank SMS format** issue template. **Anonymize first:** replace names, account and card
digits, UPI IDs, reference numbers and balances with fake values of the same shape (for example,
keep `XX1234` as `XX` plus 4 digits). Keep the sender ID (such as `AD-HDFCBK`) and the wording
exactly as they are.

## License

By contributing, you agree that your contributions are licensed under the
[AGPL-3.0](LICENSE), the same as the project.
