# Online (CICS) modernization

The 17 CICS online programs of CardDemo are re-implemented on the target architecture and proven equivalent
to the unchanged COBOL running in the Linux CICS region (`legacy-runtime/cics`).

| Legacy | Modern |
|---|---|
| CICS pseudo-conversation, COMMAREA, `EXEC CICS RETURN TRANSID` | Stateless REST API (`modern/carddemo-online`, Java 21, Spring Boot 3.3), signed session token (JWT) |
| BMS maps (`app/bms/*.bms`) | React 18 + TypeScript screens (`modern/carddemo-ui`), one screen per map, captioned `legacy: <program>/<TRANID>` |
| VSAM KSDS (ACCTDATA, CARDDATA, CARDXREF, CUSTDATA, TRANSACT, USRSEC) | PostgreSQL (Cloud SQL / AlloyDB compatible), one Flyway schema shared with the modern batch (`modern/carddemo-domain`) |
| Region on one LPAR | Cloud Run services, Memorystore cache (`redis` profile), Secret Manager (`deploy/terraform`) |

Programs: COSGN00C, COMEN01C, COADM01C, COACTVWC, COACTUPC, COCRDLIC, COCRDSLC, COCRDUPC, COTRN00C, COTRN01C,
COTRN02C, COBIL00C, COUSR00C-03C. Every legacy message is reproduced byte for byte; legacy oddities are kept and
listed in `validation/online/traceability.md` (legacy quirks). Requirement IDs (`ONL-*`) come from
`docs/BUSINESS_REQUIREMENTS.md`.

## Evidence

| What | Where |
|---|---|
| Business rules: 129 unit tests + 17 Testcontainers (PostgreSQL 16) API tests, each named `<ONL-id> <program> <paragraph>` | `modern/carddemo-online/src/test` |
| JaCoCo line / branch coverage | `modern/carddemo-online/target/site/jacoco/index.html` after `mvn verify` |
| UI parity: same journeys on 3270 and React, screenshots side by side, field-level comparison | `validation/online/report.html`, `report.json` |
| Requirement -> COBOL paragraph -> Java -> React -> test -> parity scenario | `validation/online/traceability.md` |
| Load test (indicative, local) | `reports/nfr/README.md`, `validation/nfr/online-api.js` |

## Run it from a clean checkout

Prerequisites: GnuCOBOL 3.x with BDB (see `legacy-runtime/README.md`), Python 3.10+, Java 21, Maven 3.9,
Node 20, Docker (PostgreSQL and Testcontainers).

```bash
# 1. legacy: build the batch programs (seed/POSTTRAN) and the CICS programs once
legacy-runtime/build.sh
legacy-runtime/cics/build_cics.sh

# 2. PostgreSQL for the modern API (any PostgreSQL 14+ works)
docker run -d --name carddemo-pg -e POSTGRES_DB=carddemo -e POSTGRES_USER=carddemo \
  -e POSTGRES_PASSWORD=localdev -p 5433:5432 postgres:16-alpine
export CARDDEMO_DB_URL=jdbc:postgresql://localhost:5433/carddemo CARDDEMO_DB_USER=carddemo \
  CARDDEMO_DB_PASSWORD=localdev CARDDEMO_JWT_SECRET="$(openssl rand -hex 32)"

# 3. All Java modules (domain, batch, online): unit + integration tests, coverage, jars
mvn -B -f modern/pom.xml verify

# 4. UI: lint, types, tests
(cd modern/carddemo-ui && npm ci && npm run lint && npm run typecheck && npm test)

# 5. parity: legacy batch.py POSTTRAN -> CICS region (:3270); modern batch POSTTRAN into PostgreSQL -> API (:8080,
#    fixture users only) -> UI (:4173); runs every scenario on both sides, including X01 (posted rows on COTRN00C/01C)
(cd validation/online && npm ci && ./run-parity.sh)
open validation/online/report.html
```

To explore by hand, keep the services running and use the same seed:

```bash
W=/tmp/online && python3 legacy-runtime/batch.py --workdir $W --jobs POSTTRAN
python3 legacy-runtime/cics/region.py --workdir $W --reuse --port 3270 &        # CC00, USER0001 / PASSWORD
java -jar modern/carddemo-batch/target/carddemo-batch.jar --seed-dir=app/data/ASCII --out=/tmp/online-batch \
  --carddemo.batch.load-seeds=true --carddemo.batch.job-name=POSTTRAN                # same CARDDEMO_DB_* as the API
CARDDEMO_APP_DATA=app/data CARDDEMO_SEED_MODE=users java -jar modern/carddemo-online/target/carddemo-online.jar &
(cd modern/carddemo-ui && npm run dev)                                            # http://localhost:5173
```

API documentation: `http://localhost:8080/swagger-ui.html` (OpenAPI at `/v3/api-docs`); metrics at
`/actuator/prometheus`.

## Configuration

All configuration is environment based (`application.yml`): `CARDDEMO_DB_URL/USER/PASSWORD`,
`CARDDEMO_JWT_SECRET` (required, >= 32 bytes; start-up fails without it unless `SPRING_PROFILES_ACTIVE` includes `local`, which uses an ephemeral key), `CARDDEMO_SEED_MODE` (`none` (default) | `users` | `if-empty` | `reload`),
`CARDDEMO_SEED_WORKDIR`, `CARDDEMO_CORS_ORIGINS`, `SPRING_PROFILES_ACTIVE=redis` with `REDIS_HOST` for the cache,
`PORT` (Cloud Run). Production values come from Secret Manager (see `deploy/terraform`).

Seeding is off by default. The legacy seed data includes fixture users with a known password (`ADMIN001`,
`USER0001`, ... / `PASSWORD`). They exist only for parity and demos: `run-parity.sh`, the runbook above and local
development turn seeding on explicitly: `users` loads only USRSEC (business data comes from the modern batch),
`reload` loads every dataset from a legacy workdir. Terraform and Cloud Run never set it, so a
deployed database starts with no users. Load users through a controlled bootstrap instead.

Admin role checks are not trusted from the token alone. For an admin token, `JwtAuthFilter` re-reads the user's
`user_type` from `app_user` (USRSEC) on every request (a primary-key read), so a demoted or deleted admin gets 403 on
admin APIs at once. Other users keep the 30-minute token TTL.

## Shared schema with the batch stream

On the mainframe, the batch jobs and the CICS region share the VSAM files, so transactions posted by POSTTRAN show
up on COTRN00C. The modern side keeps that: `carddemo-online` depends on `carddemo-domain` and uses its JPA entities
(`Account`, `Customer`, `Card`, `CardXref`, `CardTransaction`, `UserSecurity` -> `app_user`) and its single Flyway
history. `V1__carddemo_schema.sql` is the shared base. `V2__online_optimistic_locking.sql` adds `version` columns to
`account`, `customer` and `card` for the online READ UPDATE / REWRITE paths. The BCrypt password hash goes in
`app_user.password_hash`, which already exists in V1. The online module keeps its own query repositories
(`com.carddemo.online.repo`: pessimistic `findForUpdate`, keyed browse for F7/F8) over the shared entities.
Typed columns are rendered back to the BMS field formats by `LegacyDates`. COTRN02C writes a bare `YYYY-MM-DD` into
TRAN-ORIG-TS/TRAN-PROC-TS, which is stored as midnight of that day. The screens show only the date part, so nothing visible changes.

`run-parity.sh` proves the shared state end to end. The modern batch POSTTRAN loads `app/data/ASCII` and posts DALYTRAN
into the same PostgreSQL schema the API reads. Legacy `batch.py` POSTTRAN does the same for the CICS region's files.
Every online scenario then runs against batch-produced state, and scenario X01 (`XST-01`) checks the posted rows on
COTRN00C/COTRN01C.

## Deploy

`deploy/terraform` defines Artifact Registry, Cloud SQL for PostgreSQL 16 (private IP), Memorystore Redis,
Secret Manager secrets, and two Cloud Run services (internal API, public UI proxying `/api`).
`.github/workflows/online-modernization.yml` builds and tests on every PR; it deploys only on a manual
`workflow_dispatch` with `deploy=true` and the `GCP_*` / `TF_STATE_BUCKET` repository variables configured.
Nothing is deployed by default.

## Known gaps

- Card list / card update and user administration are covered by unit + API tests; the browser parity
  scenarios cover sign-on, menus, account view/update, transaction list/view/add and bill pay.
- Load numbers are from a single VM; they must be re-run in a GCP performance environment.

## Known limitations

- The API Cloud Run service grants `roles/run.invoker` to `allUsers`, with `INTERNAL_ONLY` ingress. Every
  `/api` route except sign-on also needs the application JWT. This is acceptable for the demo because the UI's
  nginx proxy forwards the browser's bearer token, not a Google ID token. Production alternative: make the UI service
  account the only invoker, and have the proxy (or a BFF) attach a Google ID token for the API audience. Then send
  the application JWT in a separate header.
- `/actuator/prometheus` (and `/actuator/health`, `/actuator/info`) are served without the application JWT on the
  API port. That is acceptable here because the API has `INTERNAL_ONLY` ingress: only the VPC can reach it, and the
  public UI proxy forwards only `/api`. Metrics hold request counts and latencies, not account data. Production
  alternative: set `management.server.port` to a separate port that only the scraper can reach, and keep
  `/livez` and `/readyz` on the main port with `management.endpoint.health.probes.add-additional-paths=true`.
