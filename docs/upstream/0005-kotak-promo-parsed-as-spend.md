# Draft upstream issue: Kotak card offers parsed as credit card spends

Status: draft, not submitted. Upstream: sarim2000/pennywiseai-tracker at `0623c2b`. Found on a real
phone (11 false spends in 30 days). Budget Widget ignores TRAI promotional headers (`-P`) in
`PennyWiseSmsParser` (ADR-0017).

---

**Title:** `fix(parser-core): Kotak promotional "get N points per Rs.100 spent" parsed as CREDIT`

**Body:**

```
Apply & link your Kotak Credit Card to UPI & get 5 Reward Points per Rs.100 spent. T&C: https://...
sender JD-KOTAKB-P  ->  CREDIT, amount 100
```

Suggested fixes, both cheap:
- **Promotional headers:** reject senders with the TRAI promotional suffix `-P` in `canHandle`, or in `BankParserFactory`. Indian banks send transaction alerts only on `-S`/`-T` headers.
- **Offer wording:** treat "apply", "get … points", "per Rs.N spent" as promotional in `isTransactionMessage`.
