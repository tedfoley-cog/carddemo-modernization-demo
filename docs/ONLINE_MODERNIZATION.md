# Online (CICS) modernization

The 17 CICS online programs of CardDemo are re-implemented on the target architecture and proven equivalent
to the unchanged COBOL running in the Linux CICS region (`legacy-runtime/cics`).

| Legacy | Modern |
|---|---|
| CICS pseudo-conversation, COMMAREA, `EXEC CICS RETURN TRANSID` | Stateless REST API (`modern/carddemo-online`, Java 21, Spring Boot 3.3), signed session token (JWT) |
| BMS maps (`app/bms/*.bms`) | React 18 + TypeScript screens (`modern/carddemo-ui`), one screen per map, captioned `legacy: <program>/<TRANID>` |
| VSAM KSDS (ACCTDATA, CARDDATA, CARDXREF, CUSTDATA, TRANSACT, USRSEC) | PostgreSQL (Cloud SQL / AlloyDB compatible), Flyway schema with copybook column names and widths |
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

# 3. API: unit + integration tests, coverage, jar
(cd modern/carddemo-online && mvn -B verify)

# 4. UI: lint, types, tests
(cd modern/carddemo-ui && npm ci && npm run lint && npm run typecheck && npm test)

# 5. parity: seeds a fresh legacy workdir (batch POSTTRAN), starts the CICS region (:3270),
#    the API (:8080, loading the same datasets) and the UI (:4173), runs every scenario on both sides
(cd validation/online && npm ci && ./run-parity.sh)
open validation/online/report.html
```

To explore by hand, keep the services running and use the same seed:

```bash
W=/tmp/online && python3 legacy-runtime/batch.py --workdir $W --jobs POSTTRAN
python3 legacy-runtime/cics/region.py --workdir $W --reuse --port 3270 &        # CC00, USER0001 / PASSWORD
CARDDEMO_SEED_WORKDIR=$W CARDDEMO_SEED_MODE=reload java -jar modern/carddemo-online/target/carddemo-online.jar &
(cd modern/carddemo-ui && npm run dev)                                            # http://localhost:5173
```

API documentation: `http://localhost:8080/swagger-ui.html` (OpenAPI at `/v3/api-docs`); metrics at
`/actuator/prometheus`.

## Configuration

All configuration is environment based (`application.yml`): `CARDDEMO_DB_URL/USER/PASSWORD`,
`CARDDEMO_JWT_SECRET` (required, >= 32 bytes), `CARDDEMO_SEED_MODE` (`none` | `if-empty` | `reload`),
`CARDDEMO_SEED_WORKDIR`, `CARDDEMO_CORS_ORIGINS`, `SPRING_PROFILES_ACTIVE=redis` with `REDIS_HOST` for the cache,
`PORT` (Cloud Run). Production values come from Secret Manager (see `deploy/terraform`).

## Deploy

`deploy/terraform` defines Artifact Registry, Cloud SQL for PostgreSQL 16 (private IP), Memorystore Redis,
Secret Manager secrets, and two Cloud Run services (internal API, public UI proxying `/api`).
`.github/workflows/online-modernization.yml` builds and tests on every PR; it deploys only on a manual
`workflow_dispatch` with `deploy=true` and the `GCP_*` / `TF_STATE_BUCKET` repository variables configured.
Nothing is deployed by default.

## Known gaps

- Persistence is the online module's own schema with copybook-identical table/column names; it is meant to
  converge on `modern/carddemo-domain` when the batch modernization lands.
- Card list / card update and user administration are covered by unit + API tests; the browser parity
  scenarios cover sign-on, menus, account view/update, transaction list/view/add and bill pay.
- Load numbers are from a single VM; they must be re-run in a GCP performance environment.
