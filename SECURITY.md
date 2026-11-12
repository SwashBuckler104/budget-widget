# Security policy

Budget Widget reads bank SMS, so security and privacy bugs matter more than usual.

## Supported versions

Only the latest release on [GitHub Releases](https://github.com/SwashBuckler104/budget-widget/releases)
gets fixes.

## Reporting a vulnerability

**Do not open a public issue.** Report it privately through
[GitHub private vulnerability reporting](https://github.com/SwashBuckler104/budget-widget/security/advisories/new).
Include the app version, Android version, phone model and steps to reproduce. Never attach real
SMS: replace names, account numbers, amounts and reference numbers first.

You should get a reply within 7 days. Once a fix is released, the advisory is published with
credit to you, unless you prefer to stay anonymous.

## In scope

- Anything that sends data off the phone (the app must have no `INTERNET` permission).
- Other apps reading Budget Widget's data, for example through exported components or intents.
- SMS that are not financial being stored.
- Backup or CSV export files that contain more than they should.
