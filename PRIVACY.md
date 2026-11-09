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

- **SMS** (`RECEIVE_SMS`, `READ_SMS`): to detect bank, card and UPI transaction messages. Each message is checked on the phone. Messages from senders that aren't recognised banks are ignored and never stored.
- **Notifications** (`POST_NOTIFICATIONS`): to ask you to categorize a new transaction.

## What the app stores (on your phone only)

- Transactions parsed from bank SMS: amount, direction, merchant, account's last 4 digits, time, and the original SMS text (so you can check it).
- Bank SMS the parser couldn't understand, so you can report a parser bug. This list holds at most 200 messages and you can delete it at any time.
- Your categories, merchant rules, notes and settings.

Personal SMS is never stored.

## Backups and export

CSV export and JSON backup write a file to a location **you** choose (via Android's file picker).
These files are not encrypted (encryption is planned for v1.1), so store them somewhere safe.

## Deleting your data

Uninstalling the app deletes everything it stored. Backup files you exported are not touched.

## Contact

Open an issue at <https://github.com/SwashBuckler104/budget-widget/issues>. Never paste
unredacted SMS: remove names, account numbers, references and balances first.
