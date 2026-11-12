## What and why

<!-- What changed and why. Link the issue if there is one. -->

## Checklist

- [ ] `./gradlew spotlessCheck lint test assembleDebug assembleRelease` passes
- [ ] No new permissions (or `config/permissions-allowlist.txt` updated with a reason)
- [ ] Nothing in `third_party/` changed
- [ ] Any SMS in tests or golden files is anonymized (names, account numbers, references)
- [ ] User-visible changes tested on a phone or emulator
