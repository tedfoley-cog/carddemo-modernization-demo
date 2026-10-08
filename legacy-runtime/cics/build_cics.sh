#!/usr/bin/env bash
# Build the Linux CICS region: translate + compile every online CardDemo program, generate
# file-control handlers, build the task host. TRACE=1 adds statement tracing for coverage.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; RT="$(dirname "$HERE")"; APP="$(dirname "$RT")/app"
B="$RT/build"; OUT="$B/cics-bin${TRACE:+-trace}"
mkdir -p "$B/cics-src" "$OUT" "$B/cics-fh"
for f in "$APP"/cbl/CO*.cbl; do
  p=$(basename "$f" .cbl)
  python3 "$HERE/translate.py" "$f" "$B/cics-src/$p.cbl"
  cobc -std=ibm -fsign=EBCDIC -ftab-width=4 ${TRACE:+-ftraceall} -I "$HERE/copy" -I "$APP/cpy" -I "$APP/cpy-bms" \
       -m -o "$OUT/$p.so" "$B/cics-src/$p.cbl"
done
python3 "$HERE/gen_fh.py" "$B/cics-fh"
for f in "$B"/cics-fh/FH*.cbl; do cobc -std=ibm -m -o "${f%.cbl}.so" "$f"; done
cc -O1 -Wall -rdynamic -o "$B/cicshost" "$HERE/cicshost.c" $(cob-config --cflags --libs)
echo "CICS region built: $(ls "$OUT" | wc -l) programs -> $OUT"
