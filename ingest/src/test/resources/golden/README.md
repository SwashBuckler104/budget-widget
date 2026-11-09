# Golden bank SMS

One file per v1 bank. `GoldenSmsTest` runs every case through the real parser (PennyWise
parser-core) and our mapper, so a submodule bump that changes behaviour for our banks fails the
build (SCOPE.md §5, docs/PARSER_UPGRADE.md).

## Rules

- **Anonymize first.** Replace names, account and card digits, UPI IDs, reference numbers and
  balances with fake values of the same shape. Keep the sender ID and the wording exactly.
- **Expected values come from reading the SMS**, not from running the parser.
- Seeded in M1 from the formats in upstream's own tests, with fresh fake values. Add real
  family SMS (anonymized) as they show up, especially to `jupiter.txt`.

## Format

Cases are separated by a line containing only `---`. Lines starting with `#` are comments.

| Key | Meaning |
|---|---|
| `name` | Short description (shown on failure) |
| `sender` | SMS sender ID, e.g. `AD-HDFCBK` |
| `body` | Message text; write a newline as `\n` |
| `type` | `EXPENSE`, `INCOME`, `CREDIT`, `TRANSFER`, `INVESTMENT`, `BALANCE`, `UNPARSED` or `NOT_BANK` |
| `amount` | Rupees, e.g. `150.00` (compared as paise) |
| `direction` | `OUT` or `IN` |
| `merchant`, `last4` | Exact value, or `-` for none. Omit to not check |
| `card` | `true` or `false`. Omit to not check |
| `balance` | Rupees after the transaction, or the balance of a `BALANCE` alert. Omit to not check |
| `merchant-upstream-bug` | A known wrong merchant from the upstream parser. The test expects this value instead of `merchant`, so it fails when upstream fixes the bug and the line can be removed. Link the draft in `docs/upstream/` in a comment |
