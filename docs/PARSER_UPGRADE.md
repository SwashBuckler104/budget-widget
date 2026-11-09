# Upgrading the PennyWise parser

The parser is the upstream `parser-core` from <https://github.com/sarim2000/pennywiseai-tracker>,
included as the submodule `third_party/pennywise` and compiled unmodified by `:parser-core`.
Never edit files in the submodule. Fixes go upstream (draft them in `docs/upstream/`).

## Procedure

1. **Pick the target commit** and see what changed in the parsers we depend on:
   ```sh
   cd third_party/pennywise
   git fetch origin
   git log --oneline HEAD..origin/main -- parser-core
   git diff HEAD..origin/main --stat -- parser-core
   # Our banks: review these diffs line by line.
   git diff HEAD..origin/main -- parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/{Kotak,Federal,Jupiter,Slice,Axis,SBI,HDFC}*.kt \
     parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/BankParser.kt \
     parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/BankParserFactory.kt \
     parser-core/src/main/kotlin/com/pennywiseai/parser/core/ParsedTransaction.kt \
     parser-core/src/main/kotlin/com/pennywiseai/parser/core/TransactionType.kt
   ```
   Pay attention to `ParsedTransaction.generateTransactionId()`. It is our dedupe key (`txnHash`), so any change to it re-keys every stored transaction and needs a migration plan before the bump.
2. **Check the build inputs still match** `parser-core/build.gradle.kts`: source dirs (`src/main/kotlin`, `src/commonMain/kotlin`), test dirs, and upstream's `kotlinx-datetime` version, which must equal `kotlinxDatetime` in `gradle/libs.versions.toml`.
3. **Bump:**
   ```sh
   git -C third_party/pennywise checkout <sha>
   git add third_party/pennywise
   ```
4. **Run the gates:**
   ```sh
   ./gradlew :parser-core:test      # full upstream suite + verifyOurBankTestsRan + verifySubmoduleClean
   ./gradlew :ingest:test           # our golden corpus (ingest/src/test/resources/golden/*.jsonl, from M1)
   ./gradlew spotlessCheck lint test assembleRelease
   ```
   If an upstream test class for one of our banks was renamed or moved, update `ourBankTestClasses` in `parser-core/build.gradle.kts`. Never drop a bank from the list.
5. **Update the pin references:** the commit in `NOTICE` and ADR-0001 in `docs/DECISIONS.md`.
6. **Commit** on its own branch: `build(parser): bump pennywise parser-core to <short-sha>`. Put a summary of the reviewed diff for our banks in the body.

If a golden test fails, the upgrade waits. Either upstream regressed (report it with the anonymized SMS), or our expectation was wrong (fix the corpus in a separate commit, with a reason).
