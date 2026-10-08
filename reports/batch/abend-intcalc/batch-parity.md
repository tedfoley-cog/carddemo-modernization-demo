# Batch golden-file parity

- legacy: `runs/parity/legacy/abend-intcalc/out`
- modern: `runs/parity/modern/abend-intcalc/out`

| Artifact | Produced by | Legacy recs | Modern recs | Matched | Mismatched | Missing | Extra | Result |
|---|---|---:|---:|---:|---:|---:|---:|---|
| TRANSACT.dat | POSTTRAN/COMBTRAN  transaction master (KSDS unload) | 263 | 263 | 263 | 0 | 0 | 0 | **MATCH** |
| DALYREJS.PS | POSTTRAN  rejected daily transactions | 38 | 38 | 38 | 0 | 0 | 0 | **MATCH** |
| ACCTDATA.dat | POSTTRAN/INTCALC  account master (KSDS unload) | 50 | 50 | 50 | 0 | 0 | 0 | **MATCH** |
| TCATBALF.dat | POSTTRAN  category balances (KSDS unload) | 101 | 101 | 101 | 0 | 0 | 0 | **MATCH** |
| SYSTRAN.PS | INTCALC  generated interest transactions | 50 | 50 | 50 | 0 | 0 | 0 | **MATCH** |
| RETURN-CODES.txt | JES  step condition codes (COBOL steps) | 2 | 2 | 2 | 0 | 0 | 0 | **MATCH** |
