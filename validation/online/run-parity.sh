#!/usr/bin/env bash
# Runs the online parity harness end to end: fresh legacy seed -> CICS region + modern API + UI -> report.
# Requires: legacy-runtime/build.sh and legacy-runtime/cics/build_cics.sh already run, the batch and API jars built (mvn -f modern/pom.xml package),
# PostgreSQL reachable via CARDDEMO_DB_URL/USER/PASSWORD and CARDDEMO_JWT_SECRET set. SKIP_START=1 reuses running services.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="${PARITY_WORKDIR:-$HERE/.work/legacy}"
LEGACY_PORT="${LEGACY_PORT:-3270}"
API_PORT="${API_PORT:-8080}"
UI_PORT="${UI_PORT:-4173}"
PIDS=()
cleanup() { for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT

wait_for() { for _ in $(seq 1 120); do curl -sf -o /dev/null "$1" && return 0; sleep 1; done; echo "timeout: $1" >&2; return 1; }

if [ -z "${SKIP_START:-}" ]; then
  : "${CARDDEMO_DB_URL:?set CARDDEMO_DB_URL}" "${CARDDEMO_JWT_SECRET:?set CARDDEMO_JWT_SECRET}"
  [ -d "$ROOT/legacy-runtime/build/bin" ] || { echo "run legacy-runtime/build.sh and legacy-runtime/cics/build_cics.sh first" >&2; exit 2; }
  rm -rf "$WORK" && mkdir -p "$WORK" "$HERE/.work"
  echo "== seeding legacy datasets in $WORK (batch POSTTRAN)"
  python3 "$ROOT/legacy-runtime/batch.py" --workdir "$WORK" --jobs POSTTRAN > "$HERE/.work/batch.log" 2>&1
  echo "== starting CICS region on :$LEGACY_PORT"
  python3 "$ROOT/legacy-runtime/cics/region.py" --workdir "$WORK" --reuse --port "$LEGACY_PORT" > "$HERE/.work/region.log" 2>&1 &
  PIDS+=($!)
  # Cross-stack: like batch and CICS sharing the VSAM files, the modern batch and the online API share one
  # PostgreSQL schema (carddemo-domain V1+V2). Modern POSTTRAN loads app/data/ASCII and posts DALYTRAN into
  # card_transaction/account; the API then serves that state. Only the USRSEC fixture users are seeded by the API.
  echo "== modern batch POSTTRAN into the shared PostgreSQL schema"
  "${JAVA:-java}" -jar "$ROOT/modern/carddemo-batch/target/carddemo-batch.jar" --seed-dir="$ROOT/app/data/ASCII" \
    --out="$HERE/.work/modern-batch" --carddemo.batch.load-seeds=true --carddemo.batch.job-name=POSTTRAN \
    > "$HERE/.work/modern-batch.log" 2>&1 || { echo "modern POSTTRAN failed (see .work/modern-batch.log)" >&2; exit 1; }
  grep -E " JOB " "$HERE/.work/modern-batch.log" | sed 's/^.* JOB /   JOB /' || true
  echo "== starting modern API on :$API_PORT (CARDDEMO_SEED_MODE=users: fixture users only)"
  JAR="$(ls "$ROOT"/modern/carddemo-online/target/carddemo-online*.jar | grep -v plain | head -1)"
  CARDDEMO_CORS_ORIGINS="http://localhost:$UI_PORT" SERVER_PORT="$API_PORT" CARDDEMO_APP_DATA="$ROOT/app/data" CARDDEMO_SEED_MODE=users \
    "${JAVA:-java}" -jar "$JAR" > "$HERE/.work/api.log" 2>&1 &
  PIDS+=($!)
  echo "== starting UI on :$UI_PORT"
  (cd "$ROOT/modern/carddemo-ui" && npm run build > "$HERE/.work/ui-build.log" 2>&1 && \
    CARDDEMO_API_URL="http://localhost:$API_PORT" npx vite preview --port "$UI_PORT" --strictPort > "$HERE/.work/ui.log" 2>&1) &
  PIDS+=($!)
  wait_for "http://localhost:$LEGACY_PORT/"
  wait_for "http://localhost:$API_PORT/actuator/health"
  wait_for "http://localhost:$UI_PORT/"
fi

cd "$HERE"
[ -d node_modules ] || npm ci
npx playwright install chromium > /dev/null
LEGACY_URL="http://localhost:$LEGACY_PORT" MODERN_URL="http://localhost:$UI_PORT" npm run --silent parity
