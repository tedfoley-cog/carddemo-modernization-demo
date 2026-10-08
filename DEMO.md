# Demo runbook: mainframe code -> reverse engineering -> forward engineering -> validation

Everything below runs live; no slides. Total ~45 min (RE 5, FE 15, validation 20, NFR 5).

## 0. Before the session (10 min)

```bash
legacy-runtime/build.sh && legacy-runtime/cics/build_cics.sh        # GnuCOBOL build of the original COBOL
python3 legacy-runtime/batch.py --workdir /tmp/run1 --jobs POSTTRAN  # seed VSAM-equivalent files, post the day
python3 legacy-runtime/cics/region.py --workdir /tmp/run1 --reuse --port 3270   # original CICS screens in a browser
```

Open http://localhost:3270, type `CC00` + Enter, sign in `USER0001` / `PASSWORD` (admin: `ADMIN001` / `PASSWORD`).
These are the fixture credentials shipped with CardDemo, not secrets.

## 1. Mainframe code (2 min)

`app/` is the unmodified CardDemo estate: 31 COBOL programs (17 CICS online, 12+ batch), 17 BMS maps, 38 JCL,
Control-M / CA-7 schedules, CSD, plus IMS/DB2/MQ extensions. Show the legacy running for real:

- the 3270 browser screen above — original `COSGN00C` / `COMEN01C` / `COACTVWC` COBOL, unchanged, on a Linux CICS
  compatibility layer (`legacy-runtime/cics/`); `/api/trace` shows every EXEC CICS command each task issued;
- `python3 legacy-runtime/batch.py --workdir /tmp/night` — the real POSTTRAN -> INTCALC -> COMBTRAN -> TRANREPT ->
  CREASTMT stream, byte-for-byte reproducible.

## 2. Reverse engineering (brief, 5 min)

- **DeepWiki / Ask Devin** on the repo: "Walk me through what happens to a card transaction from DALYTRAN to the
  customer statement" and "Which programs update ACCTDATA, online and batch?".
- **COBOL toolkit (mftb)** — results already in `reverse-engineering/store/` (rerun live with the commands in
  `reverse-engineering/README.md`):
  - `store/reports/estate.html` — 4 applications, 240 files, every artifact placed;
  - `store/reports/CARDDEMO/CARDDEMO-report.html` — programs, data, CICS, findings;
  - `store/reports/CARDDEMO/chains/chain-CARDDEMO.html` — nightly chain with data lineage;
  - `store/reports/CARDDEMO/online/online-CAVW.html` — one transaction's programs, maps, COMMAREA, files.
- **Business requirements**: `docs/BUSINESS_REQUIREMENTS.md` — each rule with the COBOL line that implements it,
  including 5 legacy defects the audience will recognise as typical of their own estate.

## 3. Forward engineering (15 min)

Target architecture: Java 21 / Spring Boot 3.3 on Cloud Run, Spring Batch as Cloud Run Jobs + Cloud Scheduler,
React + TypeScript, PostgreSQL (AlloyDB / Cloud SQL compatible), Memorystore, Pub/Sub, Terraform.

- `modern/carddemo-domain` — entities and Flyway schema derived from the copybooks.
- `modern/carddemo-batch` — Spring Batch jobs, one per legacy job; each step's Javadoc cites the COBOL paragraph.
- `modern/carddemo-online` — stateless REST API replacing the CICS programs (JWT replaces sign-on COMMAREA).
- `modern/carddemo-ui` — React screens mapped 1:1 to BMS maps (each shows `legacy: <program>/<TRANID>`).
- `deploy/` — Dockerfiles, Terraform, CI pipeline.

Live: ask Devin for one small change (e.g. fix BAT-POST-02 behind its switch) and watch it run the parity suite
before opening the PR.

## 4. Validation (20 min) — the core of the demo

**Batch golden files** (`validation/README.md`):

```bash
python3 -m validation.batch.scenarios edge /tmp/s-edge
python3 legacy-runtime/batch.py --workdir /tmp/legacy --data-dir /tmp/s-edge --trace
# modern run on the same seed + clock -> /tmp/modern/out
python3 -m validation.batch.compare /tmp/legacy/out /tmp/modern/out --report /tmp/parity
```

Record-by-record, field-by-field comparison by copybook key; abend scenarios must fail at the same step with
the same code. Scenarios: `edge`, `abend-intcalc`, `orphan-xref`, `volume`.

**Online parity** (`validation/online/`): the same scripted user journeys (sign-on, wrong password, account view,
invalid account, bill pay, account-update validation) driven against the 3270 screen and the React app in the
browser, screen-recorded, with the resulting VSAM / PostgreSQL state compared afterwards.

**Coverage**:
- legacy: `python3 -m validation.coverage.cobol_coverage <runs> --report <dir>` — paragraph/statement coverage of
  the COBOL from GnuCOBOL traces, listing every paragraph the scenarios never executed;
- modern: JaCoCo line/branch coverage;
- traceability: requirement -> COBOL paragraph -> Java class -> test, so an uncovered legacy paragraph is an
  explicit gap, not a silent one.

## 5. NFRs (5 min)

`reports/nfr/` — batch throughput extrapolated to ~1.4M updates/day, API load test (p95 latency, TPS) against
the targets (quote API p95 <= 200 ms, ~3,000 TPS peak, navigation < 2 s). Each report states what a single-box
measurement does and does not prove, and how Cloud Run autoscaling / AlloyDB read pools carry it to production.
