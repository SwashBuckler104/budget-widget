# Privacy

Budget Widget is built so that your financial data **cannot** leave your phone.

## No network access

The app does not request the `INTERNET` permission. Android doesn't let an app without it open a
network connection, so Budget Widget can't upload, sync, report crashes or show ads. You can check
this in *Settings → Apps → Budget Widget → Permissions*. Every build is also checked automatically
before release (`scripts/check-permissions.sh`).

Android's cloud backup and device-to-device transfer are turned off for the app, so its data isn't
copied to Google Drive either.

## What the app reads

- **SMS** (`RECEIVE_SMS`, `READ_SMS`): to detect bank, card and UPI transaction messages. Each message is checked on the phone. Messages from senders that aren't recognised banks are ignored and never stored, with one exception: a message from a registered business sender (never a phone number) that clearly reads as a bank transaction alert (a rupee amount, "credited"/"debited" or similar, an account, card or UPI mention, and no one-time password) is kept in the unparsed list, so a bank the parser doesn't know yet shows up instead of vanishing.
- **Notifications** (`POST_NOTIFICATIONS`): to ask you to categorize a new transaction.

## What the app stores (on your phone only)

- Transactions parsed from bank SMS: amount, direction, merchant, account's last 4 digits, time, and the SMS text (so you can check it). Before the text is kept, account and card numbers (all but the last 2 digits), UPI IDs, links, and reference and phone numbers are masked, the same way as in the unparsed list below; text stored unmasked by 1.0.3 and older is masked after updating, and when an older backup is restored. By default the SMS text is kept for as long as the transaction; *Settings → Keep original SMS text* can delete it after 90 or 30 days once the transaction is sorted. The amount, merchant, account and category stay. The same setting removes unparsed bank SMS older than the limit.
- Bank SMS the parser couldn't understand, so you can report a parser bug. Before a message is kept, account and card numbers (all but the last 2 digits), UPI IDs, links, and reference and phone numbers are masked. One-time passwords, offers, reminders and declined or bounced payments are never kept, even from your bank. This list holds at most 200 messages and you can delete it at any time (*Settings → Unparsed bank SMS*). If you add one as a transaction yourself, its masked text moves to that transaction as its source.
- Your categories, merchant rules, notes and settings.

Personal SMS and one-time passwords are never stored.

## Help improve the parser (optional)

*Settings → Help improve the parser* is off until you use it, and runs only when you tap
*Scan*. It reads the last 90 days of SMS on the phone, in memory, and keeps only bank
transaction messages the app couldn't read. Personal messages, one-time passwords, offers and
reminders are skipped, and so is any message mentioning a one-time password, PIN, CVV or
password. Before you see them, the messages are masked: every digit becomes 9 (account and card
numbers keep their last 2 digits), links and UPI IDs are removed, and every word that isn't on a
built-in list of banking words, bank names and dates becomes x, so names and shops never leave the
phone. Amounts are masked too unless you tick *Keep real amounts*; balances stay masked either way.
Nothing is stored and your budget is not changed. You see exactly what would
be shared and can remove any message. If you tap *Email*, your own mail app opens with the
messages addressed to the developer, and you decide whether to send it. The app itself still has
no internet access and never sends anything.

## Backups and export

CSV export and JSON backup write a file to a location **you** choose (via Android's file picker).
A backup can be protected with a password: the file is then encrypted with AES-256-GCM, using a key
derived from the password (PBKDF2-HMAC-SHA256, 600,000 rounds). The password is never stored; a
forgotten one can't be recovered. CSV files and backups saved without a password are not
encrypted, so store them somewhere safe. Restore reads at most 64 MB, so a damaged or crafted file
can't exhaust the phone's memory.

## Deleting your data

*Settings → Start fresh* deletes every transaction (categories, merchant rules and account
nicknames stay). Uninstalling the app deletes everything it stored. Backup files you exported are
not touched.

## Contact

Open an issue at <https://github.com/SwashBuckler104/budget-widget/issues>. Never paste
unredacted SMS: remove names, account numbers, references and balances first.
