# Draft upstream issue/PR: Federal Bank UPI merchant names from VPA substrings

Status: draft, not submitted. Upstream: sarim2000/pennywiseai-tracker, observed at `0623c2b`
(`parser-core` unchanged up to `17eab5e`). Found by our golden test (`ingest/src/test/resources/golden/federal.txt`).

---

**Title:** `fix(parser-core): Federal UPI merchant matches brand names inside personal VPAs`

**Body:**

`FederalBankParser.parseUPIMerchant` maps a VPA's local part to a brand with `contains`, so a
brand name that appears anywhere inside a person's VPA wins:

| SMS VPA | Merchant today | Expected |
|---|---|---|
| `ravi.m42@okaxis` | `Vi` | `ravi.m42@okaxis` |
| `vijay.k@okhdfcbank` | `Vi` | `vijay.k@okhdfcbank` |
| `nikola.r@oksbi` | `Ola` | `nikola.r@oksbi` |
| `primesh@ybl` | `Amazon Prime` | `primesh@ybl` |

Reproduce:

```
Rs 320.00 debited via UPI on 02-11-2026 09:12:45 to VPA ravi.m42@okaxis.Ref No 630112233445.Small txns?Use UPI Lite!-Federal Bank
sender AD-FEDBNK  ->  merchant "Vi"
```

Short keys (`vi`, `ola`, `jio`, `bms`, `mmt`, `oyo`, `prime`, `pvr`) are the main offenders,
because they occur inside ordinary names.

**Suggested fix:** match brands as whole tokens of the local part, split on `.`, `-`, `_` and digit
boundaries (`swiggy.food` -> `[swiggy, food]`; `ravi.m42` -> `[ravi, m]`), and keep `contains`
only for long, unambiguous keys (`swiggy`, `zomato`, `flipkart`, `irctc`, `razorpay`...). Add the
four rows above as test cases.
