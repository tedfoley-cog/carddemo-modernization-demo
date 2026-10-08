# Demo runbook: mainframe code -> reverse engineering -> forward engineering -> validation

Everything below runs live; no slides. Total ~45 min (RE 5, FE 15, validation 20, NFR 5).

## 0. Before the session (10 min)

Prerequisites: GnuCOBOL 3.x (with BDB), Python 3, Java 21, Maven, Node 20+, Docker (PostgreSQL 16).

```bash
legacy-runtime/build.sh && legacy-runtime/cics/build_cics.sh        # GnuCOBOL build of the original COBOL
TRACE=1 legacy-runtime/build.sh                                      # traced build, used for COBOL coverage
mvn -f modern/pom.xml verify                                         # modern build, unit + integration tests, JaCoCo
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
validation/batch/run_parity.sh                         # base, edge, abend-intcalc, orphan-xref -> reports/batch/<scenario>/
CARDDEMO_DB_URL=jdbc:postgresql://localhost:5432/carddemo CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=... \
MODERN_ARGS=--spring.profiles.active=postgres validation/batch/run_parity.sh base   # same, modern side on PostgreSQL 16
```

For each scenario the script generates the seed data, runs the real COBOL stream and the Spring Batch stream on
the same seed and clock, and compares the outputs record by record and field by field, keyed by the copybook key
(`python3 -m validation.batch.compare`). The abend scenarios have to fail at the same step with the same code.
The comparator exits non-zero on any missing, extra or different artifact, and also when it found nothing to compare.

**Online parity** (`validation/online/`): 10 scripted Playwright journeys, each driven against the 3270 screen
and the React app. P01-P09 cover a wrong password, admin routing, account view, invalid account ids,
account-update validation, the transaction list, bill-pay errors, a real bill payment and a transaction add.
X01 is cross-stack: the nightly POSTTRAN runs first on each side (COBOL `batch.py` into the VSAM-equivalent files,
Spring Batch into the shared PostgreSQL schema), then the posted transactions are checked on the transaction list
and transaction view (COTRN00C/COTRN01C vs React). All P0x journeys also run on that batch-produced state; the API
starts with `CARDDEMO_SEED_MODE=users`, so only the fixture users are seeded and the business data comes from batch.
The field values are compared, and the state-changing journeys read back the resulting balance and transaction
list through the screens:

```bash
CARDDEMO_DB_URL=jdbc:postgresql://localhost:5432/carddemo CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=... \
CARDDEMO_JWT_SECRET=$(openssl rand -hex 32) validation/online/run-parity.sh   # -> validation/online/report.html
```

Then the same journeys are run in Devin's browser and screen-recorded, legacy and modern side by side.

Preserved legacy behavior worth calling out: POSTTRAN replaces the transaction table before posting (the legacy
step opens TRANSACT `OUTPUT`, `app/cbl/CBTRN02C.cbl:256`), so on the shared database online-added transactions do not survive the next batch
run, exactly as on the mainframe. Other known limitations (no first-admin bootstrap with seeding off, `allUsers`
invoker with internal ingress, actuator exposure) are in `docs/ONLINE_MODERNIZATION.md`.

**Coverage**:
- legacy: `python3 -m validation.coverage.cobol_coverage <runs> --report <dir>` (run by `run_parity.sh`) — paragraph/statement coverage of
  the COBOL from GnuCOBOL traces, listing every paragraph the scenarios never executed;
- modern: JaCoCo line/branch coverage;
- traceability: requirement -> COBOL paragraph -> Java class -> test, so an uncovered legacy paragraph is an
  explicit gap, not a silent one.

## 5. NFRs (5 min)

`reports/nfr/` — batch throughput extrapolated to ~1.4M updates/day, API load test (p95 latency, TPS) against
the targets (quote API p95 <= 200 ms, ~3,000 TPS peak, navigation < 2 s). Each report states what a single-box
measurement does and does not prove, and how Cloud Run autoscaling / AlloyDB read pools carry it to production.
