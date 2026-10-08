# Batch golden-file parity

- legacy: `runs/parity/legacy/edge/out`
- modern: `runs/parity/modern/edge/out`

| Artifact | Produced by | Legacy recs | Modern recs | Matched | Mismatched | Missing | Extra | Result |
|---|---|---:|---:|---:|---:|---:|---:|---|
| TRANSACT.dat | POSTTRAN/COMBTRAN  transaction master (KSDS unload) | 319 | 319 | 319 | 0 | 0 | 0 | **MATCH** |
| DALYREJS.PS | POSTTRAN  rejected daily transactions | 42 | 42 | 42 | 0 | 0 | 0 | **MATCH** |
| ACCTDATA.dat | POSTTRAN/INTCALC  account master (KSDS unload) | 51 | 51 | 51 | 0 | 0 | 0 | **MATCH** |
| TCATBALF.dat | POSTTRAN  category balances (KSDS unload) | 102 | 102 | 102 | 0 | 0 | 0 | **MATCH** |
| SYSTRAN.PS | INTCALC  generated interest transactions | 51 | 51 | 51 | 0 | 0 | 0 | **MATCH** |
| RETURN-CODES.txt | JES  step condition codes (COBOL steps) | 4 | 4 | 4 | 0 | 0 | 0 | **MATCH** |
| TRANREPT.RPT | TRANREPT  daily transaction report | 540 | 540 | 540 | 0 | 0 | 0 | **MATCH** |
| STATEMNT.PS | CREASTMT  customer statements (text) | 1288 | 1288 | 1288 | 0 | 0 | 0 | **MATCH** |
| STATEMNT.HTML | CREASTMT  customer statements (HTML) | 6773 | 6773 | 6773 | 0 | 0 | 0 | **MATCH** |
