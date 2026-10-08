# Legacy runtime (Linux)

Runs the **unmodified CardDemo COBOL** from `app/cbl` on Linux with GnuCOBOL so the
modernized system can be compared against real legacy behaviour, not against a description of it.

```bash
legacy-runtime/build.sh                 # compile batch programs -> build/bin
TRACE=1 legacy-runtime/build.sh         # statement-trace build   -> build/trace (coverage)
python3 legacy-runtime/batch.py --workdir runs/base               # nightly stream on shipped data
python3 legacy-runtime/batch.py --workdir runs/edge --data-dir <scenario> --trace
```

## What is real and what is emulated

| Piece | Status |
|---|---|
| CBTRN02C, CBACT04C, CBTRN03C, CBSTM03A, CBSTM03B (business logic) | Original source, compiled with `-std=ibm` |
| VSAM KSDS | GnuCOBOL BDB indexed files, keys from the JCL `DEFINE CLUSTER` |
| JCL job/step/DD wiring | `batch.py` (DD names -> `DD_xxx` env vars, `PARM=` via `driver/JCLPGM.cbl`) |
| DFSORT / IDCAMS REPRO | Emulated in `batch.py` / generated `RPxxxx` COBOL programs |
| LE services `CEE3ABD`, `CEEDAYS`; asm `COBDATFT` | C stand-ins in `stubs/lecee.c` |
| `CBSTM03A` PSA/TCB/TIOT walk (diagnostic DISPLAY only) | Removed by `shims/cbstm03a.py`, rest of program unchanged |
| Clock | `COB_CURRENT_DATE` + `stubs/fixedclock.c` -> byte-reproducible runs |

Outputs land in `<workdir>/out` (KSDS unloads, rejects, report, statements, step condition codes)
and are the golden files for `validation/batch/compare.py`.
