# Draft upstream issue: own account or card returned as the merchant

Status: draft, not submitted. Upstream: sarim2000/pennywiseai-tracker at `0623c2b`. Found in our
emulator end-to-end test and pinned in `ingest/src/test/resources/golden/hdfc.txt`.

---

**Title:** `fix(parser-core): HDFC UPI debit takes "A/c XX1234" as the merchant`

**Body:**

```
Rs.350.00 debited from A/c XX8812 on 03-Nov-26 to grocer@upi (UPI Ref No 630199887766)
sender CP-HDFCBK-S  ->  merchant "A/c XX8812"   (expected "grocer@upi")
```

The format is the one in upstream's own `TestHDFCBankParser` ("UPI Debit Transaction"), whose
case doesn't assert the merchant, so the bug is invisible there. The same thing happens in other
parsers whose tests leave the merchant unchecked:

| Parser | SMS | Merchant today |
|---|---|---|
| HDFC | `Rs.350.00 debited from A/c XX8812 on 03-Nov-26 to grocer@upi (...)` | `A/c XX8812` (expected `grocer@upi`) |
| SBI | `Rs.1200 debited from A/c X5503 on 04Nov26. Avl Bal Rs.23800.00` | `A/c X5503` (expected none) |
| Jupiter | `Rs.25.00 debited to your Edge CSB Bank RuPay Credit Card ending 6788 on ...` | `your Edge CSB Bank RuPay Credit Card ending 6788` (expected none) |

Suggested fix: in the base merchant extraction, reject candidates that are the message's own
account or card phrase; for HDFC, prefer the `to <payee>` group. Assert `merchant` in these test
cases (`null` where the SMS names no payee).

Budget Widget works around this in `ParsedTransactionMapper.isOwnAccountReference`.
