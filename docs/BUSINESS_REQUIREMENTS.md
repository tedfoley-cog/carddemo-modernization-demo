# CardDemo business requirements (decomposed from the legacy COBOL)

Derived from the mftb reverse-engineering store (`reverse-engineering/store/`) and the COBOL itself.
Every requirement cites the legacy source line that implements it; the modernized code and the parity
tests reference these IDs, so each row is traceable legacy -> requirement -> Java/React -> test.

Status column: **parity** = behaviour reproduced as-is (including legacy defects, flagged), **decision** = needs a
business decision before the defect is fixed.

## Online (CICS)

| ID | Requirement | Legacy evidence | Notes |
|---|---|---|---|
| ONL-SEC-01 | User ID and password are mandatory on sign-on ("Please enter User ID ..." / "Please enter Password ...") | `app/cbl/COSGN00C.cbl:120`, `:125` | |
| ONL-SEC-02 | Credentials are checked against USRSEC; wrong password -> "Wrong Password. Try again ...", unknown user -> "User not found. Try again ..." | `app/cbl/COSGN00C.cbl:242-254` | Passwords are stored in clear text in USRSEC (tech debt; modern side hashes them) |
| ONL-SEC-03 | After sign-on, admins (type A) go to the admin menu, users to the main menu | `app/cbl/COSGN00C.cbl:230-240`, `app/csd/CARDDEMO.CSD:378` | |
| ONL-NAV-01 | Main-menu options dispatch to the program in the menu table (12 options); admin menu has 9 | `app/cpy/COMEN02Y.cpy:94-97`, `app/cpy/COADM02Y.cpy:56-59` | data-driven XCTL |
| ONL-ACV-01 | Account view requires a non-zero 11-digit account number | `app/cbl/COACTVWC.cbl:126-128` | |
| ONL-ACV-02 | Account view shows account, card xref and customer data; missing records report "not found" | `app/cbl/COACTVWC.cbl:750-849` | |
| ONL-ACU-01 | Account update validates every field (status Y/N, credit limit supplied and valid, expiry month 1-12, phone area/prefix/line rules, US state code) before allowing save | `app/cbl/COACTUPC.cbl:494-510`, `:1841-2503` | ~4,200-line program, deepest validation logic in the estate |
| ONL-ACU-02 | Update is two-step: validate ("Changes validated.Press F5 to save"), then F5 commits; concurrent change is detected and refused | `app/cbl/COACTUPC.cbl:473-477` | |
| ONL-BIL-01 | Bill pay requires an account ID ("Acct ID can NOT be empty...") and an existing account ("Account ID NOT found...") | `app/cbl/COBIL00C.cbl:161`, `:361` | |
| ONL-BIL-02 | Nothing to pay when current balance <= 0 ("You have nothing to pay...") | `app/cbl/COBIL00C.cbl:198-201` | |
| ONL-BIL-03 | On confirmation, a type-02 "BILL PAYMENT - ONLINE" transaction for the full balance is written and the balance is reduced by that amount | `app/cbl/COBIL00C.cbl:220-237` | full-balance only; no partial payment |
| ONL-TRA-01 | Transaction add requires account or card number, numeric IDs, numeric type/category and merchant ID | `app/cbl/COTRN02C.cbl:199-432` | |
| ONL-LST-01 | Card, transaction and user lists page forward/back (PF8/PF7) by key | `app/cbl/COCRDLIC.cbl`, `app/cbl/COTRN00C.cbl`, `app/cbl/COUSR00C.cbl` | browse STARTBR/READNEXT/READPREV |

## Batch

| ID | Requirement | Legacy evidence | Notes |
|---|---|---|---|
| BAT-POST-01 | Daily transactions are validated against card xref (100 invalid card) and account (101 account not found) | `app/cbl/CBTRN02C.cbl:385`, `:397` | |
| BAT-POST-02 | Reject when cycle credit - cycle debit + amount exceeds the credit limit (102) or the transaction is after account expiry (103) | `app/cbl/CBTRN02C.cbl:403-420` | **decision**: a transaction failing both is rejected as 103 only; 102 is overwritten |
| BAT-POST-03 | Valid transactions are written to TRANSACT and update the account balance and the transaction-category balance; rejects go to DALYREJS(+1) with reason and description | `app/jcl/POSTTRAN.jcl:23-42`, `app/cbl/CBTRN02C.cbl:556` | RC 4 when any reject |
| BAT-INT-01 | Monthly interest per account and category = balance x disclosure-group rate / 1200, booked as system transactions | `app/cbl/CBACT04C.cbl:462-470` | |
| BAT-INT-02 | When the account's disclosure group has no rate row, the DEFAULT group is used; if that is missing too the job abends | `app/cbl/CBACT04C.cbl:415-440` | **decision**: latent month-end abend (U0999 in the harness) |
| BAT-INT-03 | Fee computation is a stub (paragraph exists, does nothing) | `app/cbl/CBACT04C.cbl:518` | parity: modern keeps a no-op hook |
| BAT-CMB-01 | System transactions are merged into TRANSACT in key order (SORT + REPRO) | `app/jcl/COMBTRAN.jcl` | |
| BAT-RPT-01 | The transaction report lists transactions in the date range by card with page, account and grand totals | `app/cbl/CBTRN03C.cbl`, `app/jcl/TRANREPT.jcl` | |
| BAT-STM-01 | Statements are produced per card in text and HTML | `app/cbl/CBSTM03A.CBL`, `app/cbl/CBSTM03B.CBL` | **decision**: in-memory table holds 51 cards x 10 transactions; more overflows |

## Defects surfaced (keep visible on stage)

1. BAT-POST-02 — reject reason 102 silently replaced by 103.
2. BAT-INT-02 — interest run abends on a category with no DEFAULT disclosure group.
3. BAT-STM-01 — statement table bounded at `OCCURS 51` cards / `OCCURS 10` transactions (`app/cbl/CBSTM03A.CBL:226-232`).
4. CBACT04C prints "ERROR OPENING DALY REJECTS FILE" when the *disclosure group* file fails to open (`app/cbl/CBACT04C.cbl:280`).
5. ONL-SEC-02 — clear-text passwords in USRSEC.

The modernized system reproduces 1-4 by default so the golden files match, with each behind a named
switch so the fix is a recorded decision rather than an accidental difference.
