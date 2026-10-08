# Online traceability matrix

Requirement IDs come from `docs/BUSINESS_REQUIREMENTS.md`. Every JUnit test is named
`<requirement> <program> <paragraph>: <rule>` (`@DisplayName`), so `mvn test` output and the
Surefire/JaCoCo reports can be grepped by requirement or by COBOL paragraph. Parity scenarios live in
`validation/online/scenarios/online.ts` and run against the unchanged CICS region and the modern stack.

| Requirement | Legacy program / paragraph | Java (API) | React screen | Tests | Parity |
|---|---|---|---|---|---|
| ONL-SEC-01 | COSGN00C `PROCESS-ENTER-KEY` (mandatory user id / password) | `SignonService.signon`, `AuthController` | `SignOn.tsx` | `OnlineApiIT` "ONL-SEC-01 COSGN00C PROCESS-ENTER-KEY" | P03 |
| ONL-SEC-02 | COSGN00C `READ-USER-SEC-FILE` (wrong password / user not found, upper-cased credentials) | `SignonService.signon` | `SignOn.tsx` | `OnlineApiIT` "ONL-SEC-02 COSGN00C READ-USER-SEC-FILE" | P01 |
| ONL-SEC-03 | COSGN00C `READ-USER-SEC-FILE` (`SEC-USR-TYPE` A -> COADM01C, U -> COMEN01C); COMMAREA | `SignonService`, `JwtService`, `JwtAuthFilter`, `SecurityConfig` | `SignOn.tsx`, `App.tsx` route guard | `OnlineApiIT` "ONL-SEC-03 ..." (routing, 401/403, demoted admin's unexpired token -> 403 via `JwtAuthFilter` USRSEC re-check) | P02 |
| ONL-NAV-01 | COMEN01C / COADM01C `PROCESS-ENTER-KEY`, `COMEN02Y` / `COADM02Y` option tables, `PGMIDERR-ERR-PARA` | `MenuService`, `MenuController` | `Menu.tsx`, `routes.ts` | `NavigationRulesTest`, `OnlineApiIT` "ONL-NAV-01 ..." | P02, P03-P09 (menu steps) |
| ONL-ACV-01 | COACTVWC `2210-EDIT-ACCOUNT` | `AccountViewService.view` | `AccountView.tsx` | `OnlineApiIT` "ONL-ACV-01 COACTVWC 2210-EDIT-ACCOUNT" | P04 |
| ONL-ACV-02 | COACTVWC `9200-GETCARDXREF-BYACCT`, `9300-GETACCTDATA-BYACCT`, `9400-GETCUSTDATA-BYCUST`, `1200-SETUP-SCREEN-VARS` | `AccountViewService`, `LegacyFormat.currency/ssn` | `AccountView.tsx` | `OnlineApiIT` "ONL-ACV-02 ...", `LegacyFormatTest` | P03, P08 |
| ONL-ACU-01 | COACTUPC `1200-EDIT-MAP-INPUTS` and `1210`..`1280` edits, `EDIT-DATE-CCYYMMDD`, `EDIT-DATE-OF-BIRTH` | `AccountUpdateValidator`, `AccountFields` | `AccountUpdate.tsx` | `AccountUpdateValidatorTest` (parameterised, one case per paragraph), `OnlineApiIT` | P05 |
| ONL-ACU-02 | COACTUPC `9600-WRITE-PROCESSING`, `9700-CHECK-CHANGE-IN-REC` (F5 commit, stale copy) | `AccountUpdateService` | `AccountUpdate.tsx` | `OnlineApiIT` "ONL-ACU-02 ..." | - (covered by IT) |
| ONL-BIL-01 | COBIL00C `PROCESS-ENTER-KEY`, `READ-ACCTDAT-FILE` | `BillPayService` | `BillPay.tsx` | `OnlineApiIT` "ONL-BIL-01 ..." | P07 |
| ONL-BIL-02 | COBIL00C `STARTBR/READPREV-TRANSACT-FILE`, `WRITE-TRANSACT-FILE` (type 02 / cat 2 / full balance) | `BillPayService`, `TransactionIdGenerator` | `BillPay.tsx` | `OnlineApiIT` "ONL-BIL-02 + ONL-BIL-03 ..." | P08 |
| ONL-BIL-03 | COBIL00C `UPDATE-ACCTDAT-FILE` (balance to zero, "nothing to pay") | `BillPayService` | `BillPay.tsx`, `AccountView.tsx` | `OnlineApiIT` "ONL-BIL-02 + ONL-BIL-03 ..." | P08 |
| ONL-TRA-01 | COTRN02C `VALIDATE-INPUT-KEY-FIELDS`, `VALIDATE-INPUT-DATA-FIELDS`, `ADD-TRANSACTION` | `TransactionAddService`, `TransactionIdGenerator` | `TransactionAdd.tsx` | `TransactionAddRulesTest`, `OnlineApiIT` "ONL-TRA-01 ..." | P09 |
| ONL-LST-01 | COTRN00C `PROCESS-PF7/PF8-KEY`; COTRN01C `READ-TRANSACT-FILE`; COCRDLIC `9000-READ-FORWARD`/`9100-READ-BACKWARDS`; COCRDSLC; COCRDUPC; COUSR00C-03C | `TransactionQueryService`, `CardService`, `UserAdminService` | `TransactionList.tsx`, `TransactionView.tsx`, `CardList.tsx`, `CardView.tsx`, `CardUpdate.tsx`, `UserList.tsx`, `UserMaintenance.tsx` | `CardServiceRulesTest`, `OnlineApiIT` "ONL-LST-01 ..." | P06 |

## Legacy quirks (kept, not fixed)

| ID | Program / paragraph | Legacy behaviour | Modern behaviour |
|---|---|---|---|
| QUIRK-MSG-01 | COACTVWC `2210-EDIT-ACCOUNT` | Message has a double space: `Account Filter must  be a non-zero 11 digit number` | Same text, byte for byte |
| QUIRK-SEC-01 | COSGN00C `PROCESS-ENTER-KEY` | User id and password are upper-cased before the USRSEC read, so passwords are case-insensitive | Same (`SignonService`) |
| QUIRK-DATA-01 | seed data | `app/data/ASCII` has account 1 balance 194.00; after the `POSTTRAN` batch the region shows 1,288.10. Parity uses the post-batch datasets on both sides | Loader reads `<workdir>/out` first, so both sides show 1,288.10 |
| QUIRK-BIL-01 | COBIL00C / `COBIL00.bms` | No amount field: bill pay always pays the full current balance. "Non-numeric amount" cannot be entered on either side; the negative path is a non-numeric account id instead (P07) | No amount input in `BillPay.tsx` |
| QUIRK-NAV-01 | `COMEN02Y` / `COADM02Y` | 11 main and 6 admin options; the requirements text says 12 / 9 | Menus are generated from the same tables |
| QUIRK-NAV-02 | COADM01C `PGMIDERR-ERR-PARA` | Selecting an uninstalled admin program abends APCT in the Linux region instead of reaching the PGMIDERR handler | API returns the PGMIDERR handler message |
| QUIRK-CRD-01 | COCRDLIC `2210/2220` filter edits | Account filter must be exactly 11 digits and card filter exactly 16; shorter numeric values are rejected rather than prefix-matched | Same |
| QUIRK-ACU-01 | COACTUPC `1260-EDIT-US-PHONE-NUM` | A fully blank phone number is accepted | Same |
| QUIRK-ACU-02 | COACTUPC `1275-EDIT-FICO-SCORE` | Seeded FICO scores below 300 (account 1 has 274) make any update of that account fail until FICO is corrected | Same (P05) |
| QUIRK-ACU-03 | COACTUPC `1280-EDIT-US-STATE-ZIP-CD` | Seeded state/ZIP pairs such as NC/12546 fail the state-ZIP prefix table | Same |
| QUIRK-TRN-01 | COTRN02C / COBIL00C | New TRAN-ID = last key + 1 (browse from HIGH-VALUES); not safe for concurrent writers | Same scheme, serialised with a PostgreSQL advisory lock |
| QUIRK-TRN-02 | COTRN02C `VALIDATE-INPUT-DATA-FIELDS` | Dates are checked with CSUTLDTC; years before 1601 are rejected | Same |

## Legacy runtime defects found

| Component | Defect | Status |
|---|---|---|
| `legacy-runtime/cics/gen_fh.py` (generated VSAM file handlers) | `STARTBR` / `READPREV` with `RIDFLD` = HIGH-VALUES did not position at end of file as CICS does, so COBIL00C/COTRN02C computed TRAN-ID `0000000000000001` and the second payment failed with `Tran ID already exist...` | Fixed in this branch |

## Modern-only security controls (no legacy equivalent)

| Control | Where | Test |
|---|---|---|
| Seeding off by default (`CARDDEMO_SEED_MODE` defaults to `none`). Fixture users with known passwords load only when `run-parity.sh`, the runbook or local dev sets `reload`. Terraform never sets it. | `application.yml`, `SeedRunner` | `OnlineApiIT` (sets `reload` explicitly), parity run |
| Admin role re-checked against `user_security` on every request made with an admin token, so a demoted admin gets 403 right away. Other tokens keep the 30-minute TTL. | `JwtAuthFilter.stillAdmin` | `OnlineApiIT` "ONL-SEC-03 COADM01C admin re-check ..." |
| A missing or short (< 32 bytes) `CARDDEMO_JWT_SECRET` stops start-up, so instances never fall back to their own per-instance keys. Only the explicit `local` profile uses an ephemeral key. | `JwtService` constructor | `JwtServiceTest` "ONL-SEC-03 COMMAREA replacement ..." |
