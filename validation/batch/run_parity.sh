#!/usr/bin/env bash
# Golden-file parity: legacy COBOL (GnuCOBOL runtime) vs modern Spring Batch, per scenario.
# Usage: validation/batch/run_parity.sh [scenario ...]   (default: base edge abend-intcalc orphan-xref)
# Env:   MODERN_JAR (default modern/carddemo-batch/target/carddemo-batch.jar)
#        MODERN_ARGS extra args for the modern CLI, e.g. --spring.profiles.active=postgres
#        JAVA (default: java on PATH)
set -uo pipefail
cd "$(dirname "$0")/../.."

SCENARIOS=("$@")
[ ${#SCENARIOS[@]} -eq 0 ] && SCENARIOS=(base edge abend-intcalc orphan-xref)
JAR=${MODERN_JAR:-modern/carddemo-batch/target/carddemo-batch.jar}
JAVA=${JAVA:-java}
WORK=${PARITY_WORK:-runs/parity}
[ -f "$JAR" ] || { echo "missing $JAR - run: mvn -f modern/pom.xml verify" >&2; exit 2; }

status=0
legacy_runs=()
for s in "${SCENARIOS[@]}"; do
  echo "=== scenario $s"
  data="$WORK/data/$s"
  rm -rf "$data" "$WORK/legacy/$s" "$WORK/modern/$s"
  mkdir -p "$data" "$WORK/legacy" "$WORK/modern/$s"
  if [ "$s" = base ]; then
    cp app/data/ASCII/*.txt "$data/"
  else
    python3 -m validation.batch.scenarios "$s" "$data" || { status=1; continue; }
  fi

  python3 legacy-runtime/batch.py --workdir "$WORK/legacy/$s" --data-dir "$data" --trace \
    > "$WORK/legacy/$s.log" 2>&1
  if [ ! -f "$WORK/legacy/$s/out/RETURN-CODES.txt" ]; then
    echo "  legacy run produced no artifacts (see $WORK/legacy/$s.log)"; status=1; continue
  fi
  legacy_runs+=("$WORK/legacy/$s")

  start=$(date +%s.%N)
  "$JAVA" -jar "$JAR" --seed-dir="$data" --out="$WORK/modern/$s/out" \
    --carddemo.batch.load-seeds=true --carddemo.batch.seed-defaults=true ${MODERN_ARGS:-} \
    > "$WORK/modern/$s.log" 2>&1
  echo "  modern exit=$? ($(echo "$(date +%s.%N) - $start" | bc | cut -c1-6)s)"
  grep -E " JOB " "$WORK/modern/$s.log" | sed 's/^.* JOB /  JOB /'

  if [ ! -f "$WORK/modern/$s/out/RETURN-CODES.txt" ]; then
    echo "  modern run produced no artifacts (see $WORK/modern/$s.log)"; status=1; continue
  fi
  mkdir -p "reports/batch/$s"
  if ! python3 -m validation.batch.compare "$WORK/legacy/$s/out" "$WORK/modern/$s/out" --report "reports/batch/$s"; then
    echo "  MISMATCH in $s (see reports/batch/$s)"
    status=1
  fi
done

if [ ${#legacy_runs[@]} -gt 0 ]; then
  mkdir -p reports/coverage
  python3 -m validation.coverage.cobol_coverage "${legacy_runs[@]}" --report reports/coverage || status=1
fi
exit $status
