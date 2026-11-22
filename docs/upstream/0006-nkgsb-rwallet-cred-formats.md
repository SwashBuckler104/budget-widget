# Draft upstream issue: NKGSB Bank, Indian Railways R-Wallet and CRED wallet formats

Status: draft, not submitted. Upstream: sarim2000/pennywiseai-tracker at `0623c2b`. Found in the
owner's parser feedback (2026-09 to 2026-11). Budget Widget reads these in its own
`LocalFormats` (ADR-0061) until upstream has them; drop each local format once upstream parses
the golden cases in `ingest/src/test/resources/golden/{nkgsb,irctc,cred}.txt`.

---

**Title:** `feat(parser-core): NKGSB Co-op Bank, Indian Railways R-Wallet and CRED wallet`

**Body:**

No parser claims these senders today, so their alerts are dropped. Example templates (values made
up):

```
VA-NKGSBK-S  NKGSB a/c X417 is CREDITED INR 2.03 on 30-09-2026 300000000123456:Int.Pd:01-07-2026 to 30-09-2026.Clr bal is INR 845.60.Not You?Call 18001234567
AD-NKGSBK-S  NKGSB a/c X417 is DEBITED INR 2.03 on 01-10-2026 NACH ACH LIEN FUND RECOVERED.Clr bal is INR 843.57.Not You?Call 18001234567
VM-IRSMSa-G  Rs.500.00 credited to your RWallet Account on Oct 12 2026  9:28AM .Now the Available Balance is Rs.504.50. IR-CRIS
VM-IRSMSa-G  Rs.20.00 debited from your RWallet Account on Oct 21 2026  9:31AM . Available Balance Rs.465.70. Indian Railway
CP-CREDWL-S  Update: ₹25 has been successfully credited to your CRED wallet. Your updated CRED wallet balance is ₹125.
CP-CREDWL-S  Update from CRED wallet:\n\nYou have successfully paid Rs.1.0 and your updated wallet balance is Rs.124.0.
```

Suggested parsing:
- **NKGSB** (`NKGSBK`): `a/c X<digits> is CREDITED|DEBITED INR <amount>`, balance after `Clr bal is INR`. The account shows only its last 3 digits.
- **R-Wallet** (`IRSMS`, mixed case `IRSMSa`, government `-G` header): `Rs.<amount> credited to|debited from your RWallet Account`, balance after `Available Balance (is)`. Debits are ticket purchases; a credit is a top-up or a refund.
- **CRED wallet** (`CREDWL`): `₹|Rs.<amount> has been successfully credited to your CRED wallet`, `You have successfully paid Rs.<amount>`, balance after `wallet balance is`.

Also, Kotak credit card spends in foreign currency (`USD 23.6 spent on Kotak Credit Card xXX82 on 06-09-26 at <MERCHANT>. Avl limit INR 41322.26`) aren't parsed at all; returning them with `currency = USD` would let apps decide.
