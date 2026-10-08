# Validation harness

Proves the modernized system is functionally equivalent to the legacy COBOL.

## Batch: golden-file equivalence
1. `python3 -m validation.batch.scenarios {edge|abend-intcalc|orphan-xref|volume} <dir>` builds deterministic mock data.
2. `python3 legacy-runtime/batch.py --workdir <legacy> [--data-dir <dir>] --trace` runs the real COBOL stream.
3. The modern batch runs on the same seed files and writes the same artifacts to `<modern>/out`.
4. `python3 -m validation.batch.compare <legacy>/out <modern>/out --report <dir>` matches records by copybook key and
   diffs every field. No normalisation: same input + same business clock must give the same bytes.

### Contract for the modernized batch
The modern batch must accept: a seed directory (same ASCII files as `app/data/ASCII`, any subset overriding the
defaults), the business clock (default `2022-07-06T10:00:00`), the INTCALC date parm (`2022071800`), the TRANREPT
date range (`2022-01-01`..`2022-07-06`), and an output directory, and produce in it:

| File | LRECL | Layout |
|---|---:|---|
| `TRANSACT.dat` | 350 | CVTRA05Y, ordered by TRAN-ID |
| `DALYREJS.PS` | 430 | CVTRA06Y + reason 9(04) + desc X(76) |
| `ACCTDATA.dat` | 300 | CVACT01Y, ordered by ACCT-ID |
| `TCATBALF.dat` | 50 | CVTRA01Y, ordered by key |
| `SYSTRAN.PS` | 350 | CVTRA05Y |
| `TRANREPT.RPT` | 133 | report lines |
| `STATEMNT.PS` / `STATEMNT.HTML` | 80 / 100 | statement lines |
| `RETURN-CODES.txt` | 80 | `<JOB>.<STEP>.<PROGRAM>` padded to 40, then `RC=nnnn` or `ABEND=Unnnn` |

Abend scenarios are part of parity: the modern job must fail at the same step with the same code.

The Java CLI under `modern/` loads seeds only with `--carddemo.batch.load-seeds=true`, and falls back to the bundled
extracts for files missing from the seed directory only with `--carddemo.batch.seed-defaults=true`. Both are off by
default so a scheduled run never replaces live data or mixes in sample data; `validation/batch/run_parity.sh` passes both.

## Online parity and cross-stack state
`validation/online/run-parity.sh` drives the same abstract user steps against the CICS region (3270 JSON API) and the
React UI. Each side starts from its own batch stream. Legacy `batch.py` POSTTRAN writes the VSAM-equivalent files the
region serves. The modern batch POSTTRAN (`carddemo-batch.jar --carddemo.batch.job-name=POSTTRAN`) writes the PostgreSQL
schema the online API reads. Both modules use the one Flyway schema in `modern/carddemo-domain`. Scenario X01
(`XST-01`) checks that DALYTRAN records posted by batch appear on COTRN00C/COTRN01C on both sides. Report:
`validation/online/report.html`, traceability: `validation/online/traceability.md`.

## Coverage
`python3 -m validation.coverage.cobol_coverage <legacy-run>... --report <dir>` turns GnuCOBOL statement traces into
paragraph/statement coverage of the legacy programs across all scenarios, listing every unexecuted paragraph.
