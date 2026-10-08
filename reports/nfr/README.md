# Online API NFR run (indicative)

Script: `validation/nfr/online-api.js` (k6 v0.54). Mix per virtual user: sign-on, then 5 x (account view
COACTVWC + transaction list COTRN00C). Half the users sign on as ADMIN001, half as USER0001.

**These are local, single-machine numbers** (8 vCPU / 31 GB VM; API, PostgreSQL 16 in Docker and k6 on the same
host; no Redis; no network hop). They show the service has no obvious hot spots at the target concurrency. They
are **not** production evidence: Cloud Run cold starts, Cloud SQL network latency, AlloyDB/Cloud SQL sizing and the
~3,000 TPS enterprise peak must be validated in a GCP performance environment with the same script.

| Run | VUs | Think time | Throughput | Errors |
|---|---|---|---|---|
| `k6-60users-think1s.json` (realistic admin/operator pacing) | 60 | 1 s | 56.5 req/s | 0.00% |
| `k6-60users-nothink.json` (saturation) | 60 | 0 s | 1,160 req/s | 0.00% |

Latency in ms:

| Operation | Run | p50 | p95 | p99 | max | Target | Result |
|---|---|---|---|---|---|---|---|
| Account view `GET /api/v1/accounts` | think 1 s | 1.9 | 4.5 | 6.5 | 89.3 | read p95 <= 200 | met |
| Transaction list `GET /api/v1/transactions` | think 1 s | 1.6 | 3.6 | 5.3 | 14.9 | read p95 <= 200 | met |
| Sign-on `POST /api/v1/auth/signon` | think 1 s | 66.4 | 86.1 | 115.1 | 170.0 | navigation < 2 s | met |
| Account view | saturation | 8.9 | 28.6 | 48.9 | 158.0 | read p95 <= 200 | met |
| Transaction list | saturation | 7.7 | 25.2 | 45.1 | 147.8 | read p95 <= 200 | met |
| Sign-on | saturation | 377.8 | 529.5 | 579.6 | 689.2 | navigation < 2 s | met |

Sign-on is the most expensive call because it verifies a BCrypt password hash and signs a token per request; it is CPU bound and scales
horizontally on Cloud Run. Reproduce with:

```bash
k6 run -e API_URL=http://localhost:8080 -e USERS=60 -e DURATION=90s -e THINK_SECONDS=1 validation/nfr/online-api.js
SUMMARY_JSON=reports/nfr/k6-60users-nothink.json k6 run -e USERS=60 -e DURATION=45s -e THINK_SECONDS=0 validation/nfr/online-api.js
```
