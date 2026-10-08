#!/usr/bin/env bash
# Build the CardDemo batch programs for Linux with GnuCOBOL.
#   legacy-runtime/build.sh            -> build/bin   (production build)
#   TRACE=1 legacy-runtime/build.sh    -> build/trace (paragraph-trace build for coverage)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$HERE/../app"
if [[ "${TRACE:-0}" == "1" ]]; then OUT="$HERE/build/trace"; TRACEFLAGS="-ftraceall"; else OUT="$HERE/build/bin"; TRACEFLAGS=""; fi
SRC="$HERE/build/src"
mkdir -p "$OUT" "$SRC"
# -std=ibm      : IBM Enterprise COBOL dialect
# -fsign=EBCDIC : zoned-decimal signs use EBCDIC overpunch ({ A-I } J-R), as in the ASCII seed data
# -ftab-width=4 : some copybooks (CUSTREC.cpy) contain tab characters
COBFLAGS=(-std=ibm -fsign=EBCDIC -ftab-width=4 -I "$APP/cpy" $TRACEFLAGS)

BATCH=(CBTRN01C CBTRN02C CBTRN03C CBACT01C CBACT02C CBACT03C CBACT04C CBCUS01C CBSTM03B)
for p in "${BATCH[@]}"; do
  f=$(ls "$APP"/cbl/$p.* | head -1)
  cobc "${COBFLAGS[@]}" -m -o "$OUT/$p.so" "$f"
done
python3 "$HERE/shims/cbstm03a.py" "$APP/cbl/CBSTM03A.CBL" "$SRC/CBSTM03A.cbl"
cobc "${COBFLAGS[@]}" -m -o "$OUT/CBSTM03A.so" "$SRC/CBSTM03A.cbl"
cobc "${COBFLAGS[@]}" -m -o "$OUT/CSUTLDTC.so" "$APP/cbl/CSUTLDTC.cbl"

cobc -m -o "$OUT/CEE3ABD.so" "$HERE/stubs/lecee.c"
for s in CEEDAYS COBDATFT; do ln -sf CEE3ABD.so "$OUT/$s.so"; done
gcc -shared -fPIC -o "$OUT/fixedclock.so" "$HERE/stubs/fixedclock.c" -ldl
cobc -std=ibm -x -o "$OUT/jclpgm" "$HERE/driver/JCLPGM.cbl"

python3 "$HERE/gen_repro.py" "$SRC/repro"
for f in "$SRC"/repro/*.cbl; do cobc -std=ibm -x -o "$OUT/$(basename "$f" .cbl)" "$f"; done
echo "built $(ls "$OUT" | wc -l) modules into $OUT"
