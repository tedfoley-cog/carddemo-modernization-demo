# Batch golden-file parity

- legacy: `runs/parity/legacy/base/out`
- modern: `runs/parity/modern/base/out`

| Artifact | Produced by | Legacy recs | Modern recs | Matched | Mismatched | Missing | Extra | Result |
|---|---|---:|---:|---:|---:|---:|---:|---|
| TRANSACT.dat | POSTTRAN/COMBTRAN  transaction master (KSDS unload) | 312 | 312 | 312 | 0 | 0 | 0 | **MATCH** |
| DALYREJS.PS | POSTTRAN  rejected daily transactions | 38 | 38 | 38 | 0 | 0 | 0 | **MATCH** |
| ACCTDATA.dat | POSTTRAN/INTCALC  account master (KSDS unload) | 50 | 50 | 50 | 0 | 0 | 0 | **MATCH** |
| TCATBALF.dat | POSTTRAN  category balances (KSDS unload) | 100 | 100 | 100 | 0 | 0 | 0 | **MATCH** |
| SYSTRAN.PS | INTCALC  generated interest transactions | 50 | 50 | 50 | 0 | 0 | 0 | **MATCH** |
| RETURN-CODES.txt | JES  step condition codes (COBOL steps) | 4 | 4 | 4 | 0 | 0 | 0 | **MATCH** |
| TRANREPT.RPT | TRANREPT  daily transaction report | 519 | 519 | 519 | 0 | 0 | 0 | **MATCH** |
| STATEMNT.PS | CREASTMT  customer statements (text) | 1262 | 1262 | 1262 | 0 | 0 | 0 | **MATCH** |
| STATEMNT.HTML | CREASTMT  customer statements (HTML) | 6632 | 6632 | 6632 | 0 | 0 | 0 | **MATCH** |
