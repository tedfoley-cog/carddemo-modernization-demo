# Nightly batch throughput: legacy vs modern

## Setup

- Data: `python3 -m validation.batch.scenarios volume /tmp/vol --accounts 5000 --txns 100000`. The generated `tcatbal.txt` still contains the 50 category-balance rows of the shipped data, for accounts 1-50. Those accounts are not in the generated `acctdata.txt`, so the **legacy** INTCALC abends U0999 on the first record ("ACCOUNT NOT FOUND: 00000000001"). The modern stream does the same. For timing, both sides were run on a copy with an empty `tcatbal.txt` (POSTTRAN then creates every category balance). The harness file was not changed. See "Harness notes" in the PR.
- Jobs: POSTTRAN, INTCALC, TRANREPT. CREASTMT is excluded because 5,000 cards exceed the legacy 51-card statement table. COMBTRAN is not selected, on either side.
- Machine: one Devin VM (single container), everything local. Each figure is the median of 3 runs. Raw output: `batch-throughput-raw.txt`.
- Legacy: GnuCOBOL 3 programs on the harness's indexed-file runtime (`legacy-runtime/batch.py`). The harness has no per-step timer. Per-job legacy times are therefore differences between cumulative `--jobs` runs. The POSTTRAN figure includes harness setup (IDCAMS DEFINE + REPRO of the seeds), so it is an upper bound.
- Modern: `java -jar carddemo-batch.jar` with `CARDDEMO_JOB_NAME=POSTTRAN,INTCALC,TRANREPT`. Per-job times are the Spring Batch job durations from the `JOB ... elapsedMs=` log line. H2 is embedded, in PostgreSQL mode. PostgreSQL is `postgres:16` in Docker on the same VM.

## Results

| Job | Records | Legacy wall | Legacy rec/s | Modern H2 wall | Modern H2 rec/s | Modern PG16 wall | Modern PG16 rec/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| POSTTRAN (CBTRN02C) | 100,000 daily txns | ≤ 1.15 s | ≥ 86,700 | 15.28 s | 6,540 | 14.88 s | 6,720 |
| INTCALC (CBACT04C) | 14,452 TCATBAL rows | 0.07 s | ~205,000 | 8.95 s | 1,610 | 9.07 s | 1,590 |
| TRANREPT (CBTRN03C) | 40,189 txns in range | 0.29 s | ~136,000 | 0.65 s | 61,400 | 0.66 s | 61,100 |
| Whole run (incl. start-up and seed load) | | 1.52 s | | 29.2 s | | 29.2 s | |

Modern start-up is about 2.7 s (JVM, Spring context, Flyway). Seed load (20,000 master rows) and the jobs make up the rest.

## Extrapolation to the customer volume

The target is about 500 million pricing updates a year, which is about **1.4 M a day**. The measured single-container POSTTRAN rate is about 6,500 records/s.

- 1.4 M records ÷ 6,500/s ≈ **215 s (about 3.6 min)** of posting per day.
- That is roughly 1/400th of a 24 h window. For a 1-hour batch window, the requirement is about 390 records/s, which is about 6% of the measured rate.
- INTCALC scales with category balances (accounts × categories), not with daily volume. At 1,600 rows/s, a 1 M-row TCATBAL table needs about 10 min.

## What this does and does not prove

It **does** show:

- The modern jobs process the volume data correctly and end-to-end on both H2 and PostgreSQL 16, with the same return codes as legacy (POSTTRAN RC=4, others RC=0).
- On one container, posting throughput is about 17x the average rate the customer volume needs.

It does **not** show:

- **Production database latency.** The database was on the same VM, so each round trip took microseconds. AlloyDB or Cloud SQL over a VPC typically adds 0.3-1 ms per round trip. The current POSTTRAN does about 4 round trips per record (xref, account, category balance, insert). At 0.5 ms that would cost about 2 ms per record on the network alone, about 47 min for 1.4 M records, unless round trips are batched. **Before production sizing, re-measure against AlloyDB from Cloud Run.**
- **Horizontal scaling.** Everything ran in one container and one thread. Spring Batch partitioning (for example, by account range) and Cloud Run Jobs task parallelism were not used or measured.
- **That modern is faster than legacy.** It is not, at this size: in-process indexed files beat ORM calls to a database. Legacy timings are from the GnuCOBOL emulation on this VM, not from z/OS DFSMS/VSAM, so they don't predict mainframe MIPS or elapsed time either.
- **Behaviour under contention, recovery and restart, or sustained load.** Each run was a single cold batch, not a soak test.

## Tuning headroom (not applied, to keep parity code simple)

- JDBC batching (`hibernate.jdbc.batch_size`, `order_inserts`) for the transaction and category-balance writes.
- Pre-loading the xref and account working sets per chunk, instead of a `findById` per record.
- INTCALC: index or cache the xref lookup by account (`CardXrefRepository.findFirstByAccountIdOrderByCardNumber`) and the disclosure-group lookups. Together these are most of the 9 s.
- Partitioned steps, with Cloud Run Jobs `--tasks` for parallel account ranges.
