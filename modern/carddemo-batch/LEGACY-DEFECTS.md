# Legacy defects reproduced by the modern batch

The modern stream reproduces each of these on purpose so the golden files match byte for byte (see
`reports/batch/*/batch-parity.md`). Each one has a fix behind a switch under
`carddemo.batch.legacy-fixes.*` in `CardDemoBatchProperties`. **Every switch is off by default.**
Turning one on is a business decision (see `docs/BUSINESS_REQUIREMENTS.md`, status *decision*). After
that change the golden files will differ on purpose.

| # | Requirement | Defect | Evidence | Modern location | Fix switch (off) |
|---|---|---|---|---|---|
| 1 | BAT-POST-02 | When a transaction is over the limit *and* after account expiry, reason 102 is overwritten by 103. The reject shows only the expiry. | `app/cbl/CBTRN02C.cbl:407-420` (MOVE 102 at :410, unconditional expiry check moves 103 at :417) | `TransactionPostingService.validateAccount` | `keep-overlimit-reason` |
| 2 | BAT-POST-02 | The limit check uses `ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT`, not the current balance. `WS-TEMP-BAL` is `PIC S9(09)V99`, so very large amounts lose their high-order digits and can pass the check (the edge scenario's 999,999,999.99 purchase is posted). | `app/cbl/CBTRN02C.cbl:187`, `:403-407` | `TransactionPostingService.validateAccount` | `limit-check-uses-current-balance` |
| 3 | BAT-INT-02 | If a posted type/category has no DISCGRP row for the account group *or* for `DEFAULT`, INTCALC abends U0999. Accounts are not updated. Interest records already written to SYSTRAN stay there. | `app/cbl/CBACT04C.cbl:415-456` (DEFAULT at :437, abend via 9999-ABEND-PROGRAM, ABCODE 999 at :631) | `InterestCalculationService.interestRate` | `skip-missing-disclosure-group` |
| 4 | BAT-STM-01 | The statement table is `OCCURS 51` cards x `OCCURS 10` transactions. An 11th transaction for a card overwrites the next card's slot, so the edge scenario's card `0500024453765740` prints a corrupted line. More than 51 cards goes past the table, which is undefined behaviour. The modern code refuses that case instead of guessing. | `app/cbl/CBSTM03A.CBL:226-232` | `StatementTable`, `StatementGenerator.loadTransactions` | `unbounded-statement-table` |
| 5 | BAT-STM-01 | An xref whose account or customer is missing calls `CEE3ABD` with no code (U0000). The stream stops after the statements already written. | `app/cbl/CBSTM03A.CBL:923` (9999-ABEND-PROGRAM, reached from the M03B keyed reads) | `StatementGenerator.generate` | `skip-orphan-xref` |
| 6 | BAT-INT-01 | The **last** account in TCATBALF never gets its interest added, and its cycle buckets are never reset. The EOF `ELSE PERFORM 1050-UPDATE-ACCOUNT` can't be reached, because the END-OF-FILE flag is set inside the same iteration. (Found during parity: account 50 in the base data.) | `app/cbl/CBACT04C.cbl:189-221` (unreachable :220) | `InterestCalculationService.calculate` | `update-last-interest-account` |
| 7 | BAT-RPT-01 | At end of file, TRANREPT adds the last transaction's amount to the page and account totals a second time before printing the final page and grand totals. It also never prints an account total for the last card. | `app/cbl/CBTRN03C.cbl:285-289` (ADD TRAN-AMT at :287) | `TransactionReport.finish` | `report-no-double-count-last-amount` |
| 8 | - | When the *disclosure group* file fails to open, INTCALC prints "ERROR OPENING DALY REJECTS FILE". This affects only a diagnostic message, not data. Not reproduced: the modern code logs what really failed. | `app/cbl/CBACT04C.cbl:281` | n/a | n/a |

## Proposed fixes (not applied)

1. Check expiry first and report it only when the limit check passed, or report both reasons. Switch: `keep-overlimit-reason=true` keeps 102.
2. Use `ACCT-CURR-BAL + amount` against `ACCT-CREDIT-LIMIT`, with a work field that can't overflow. Switch: `limit-check-uses-current-balance=true`.
3. Treat a missing rate as 0% and raise an operational alert instead of abending the month-end run. Better still, enforce DISCGRP completeness when categories are created. Switch: `skip-missing-disclosure-group=true`.
4. Size the table from the data. The modern fix allocates it from the actual card and transaction counts (`unbounded-statement-table=true`).
5. Skip the orphan xref and report it, rather than abending the whole statement run (`skip-orphan-xref=true`).
6. Perform `1050-UPDATE-ACCOUNT` after the loop when at least one record was read (`update-last-interest-account=true`).
7. Drop the extra ADD at EOF and write the final account total (`report-no-double-count-last-amount=true` removes the double count).
8. Correct the message text. Cosmetic only.
