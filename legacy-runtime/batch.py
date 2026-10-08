"""Run the CardDemo nightly batch stream on Linux against the real COBOL load modules.

Job order and DD wiring follow app/jcl: POSTTRAN -> INTCALC -> COMBTRAN -> TRANREPT -> CREASTMT.
COBOL steps run the GnuCOBOL-compiled CardDemo programs unchanged; utility steps
(IDCAMS REPRO, DFSORT) are emulated here and are the only non-COBOL logic.

    python3 legacy-runtime/batch.py --workdir /tmp/run1 [--dalytran file] [--trace]
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog import DATASETS, fixed_records, records  # noqa: E402

HERE = Path(__file__).resolve().parent
DEFAULT_CLOCK = "2022/07/06 10:00:00"   # business date of the run (COB_CURRENT_DATE)
INTCALC_PARM = "2022071800"             # PARM= on INTCALC.jcl STEP15
TRANREPT_DATES = ("2022-01-01", "2022-07-06")  # SYMNAMES in TRANREPT.JCL


class StepFailed(Exception):
    def __init__(self, step, rc):
        super().__init__(f"{step} RC={rc}")
        self.step, self.rc = step, rc


class Runner:
    def __init__(self, workdir: Path, trace: bool, clock: str):
        self.w = workdir
        self.bin = HERE / "build" / ("trace" if trace else "bin")
        self.trace = trace
        self.clock = clock
        self.steps = []
        (self.w / "ds").mkdir(parents=True, exist_ok=True)
        (self.w / "log").mkdir(exist_ok=True)

    def ds(self, name):
        return str(self.w / "ds" / name)

    def _env(self, dds, extra=None):
        env = dict(os.environ)
        env.update({
            "COB_LIBRARY_PATH": str(self.bin),
            "COB_CURRENT_DATE": self.clock,
            "COB_FILE_PATH": "",
            "LD_PRELOAD": str(self.bin / "fixedclock.so"),
        })
        for dd, path in dds.items():
            env[f"DD_{dd}"] = path
        if extra:
            env.update(extra)
        return env

    def _run(self, step, argv, env, ok=(0, 4)):
        log = self.w / "log" / f"{step}.log"
        r = subprocess.run(argv, env=env, capture_output=True, text=True, errors="replace")
        log.write_text(r.stdout + r.stderr)
        abend = re.search(r"USER ABEND (U\d{4})", r.stdout)
        cc = abend.group(1) if abend else f"{r.returncode:04d}"
        self.steps.append({"step": step, "rc": r.returncode, "cc": cc})
        print(f"  {step:<28} {'ABEND=' if abend else 'RC='}{cc}")
        if r.returncode not in ok:
            print(r.stdout[-1500:], r.stderr[-1500:])
            raise StepFailed(step, r.returncode)
        return r

    def pgm(self, step, program, dds, parm="", ok=(0, 4)):
        extra = {"JCL_PGM": program, "JCL_PARM": parm}
        if self.trace:
            extra.update({"COB_SET_TRACE": "Y", "COB_TRACE_FILE": str(self.w / "log" / f"{step}.trace")})
        return self._run(step, [str(self.bin / "jclpgm")], self._env(dds, extra), ok)

    # ---- utility emulation -------------------------------------------------
    def repro_load(self, step, name, ps_path, ks_path):
        self._run(step, [str(self.bin / f"RP{name}")],
                  self._env({"PSFILE": ps_path, "KSFILE": ks_path}, {"REPRO_MODE": "LOAD"}), ok=(0,))

    def repro_unload(self, step, name, ks_path, ps_path):
        self._run(step, [str(self.bin / f"RP{name}")],
                  self._env({"PSFILE": ps_path, "KSFILE": ks_path}, {"REPRO_MODE": "UNLOAD"}), ok=(0,))

    def sort(self, step, inputs, out, lrecl, key, include=None, outrec=None):
        recs = []
        for p in inputs:
            recs += records(Path(p).read_bytes(), lrecl)
        if include:
            recs = [r for r in recs if include(r)]
        recs.sort(key=key)  # stable, i.e. DFSORT EQUALS
        if outrec:
            recs = [outrec(r) for r in recs]
        Path(out).write_bytes(b"".join(recs))
        self.steps.append({"step": step, "rc": 0, "cc": "0000", "records": len(recs)})
        print(f"  {step:<28} RC=0000  ({len(recs)} records)")


def setup_files(r: Runner, data_dir: Path | None):
    print(f"SETUP  (IDCAMS DEFINE + REPRO of seed data{' from ' + str(data_dir) if data_dir else ''})")
    for name, ds in DATASETS.items():
        if name in ("TRXFL",):
            continue
        src = data_dir / ds.ascii_src if (data_dir and ds.ascii_src and (data_dir / ds.ascii_src).exists()) else None
        data = fixed_records(ds, src)
        ps = r.ds(f"{name}.PS")
        Path(ps).write_bytes(data)
        if ds.key:
            r.repro_load(f"LOAD.{name}", name, ps, r.ds(f"{name}.KSDS"))


def posttran(r: Runner):
    print("POSTTRAN  (CBTRN02C - post daily transactions)")
    r.pgm("POSTTRAN.STEP15.CBTRN02C", "CBTRN02C", {
        "TRANFILE": r.ds("TRANSACT.KSDS"), "DALYTRAN": r.ds("DALYTRAN.PS"),
        "XREFFILE": r.ds("CARDXREF.KSDS"), "DALYREJS": r.ds("DALYREJS.PS"),
        "ACCTFILE": r.ds("ACCTDATA.KSDS"), "TCATBALF": r.ds("TCATBALF.KSDS")})


def intcalc(r: Runner):
    print("INTCALC  (CBACT04C - interest & fees)")
    r.pgm("INTCALC.STEP15.CBACT04C", "CBACT04C", {
        "TCATBALF": r.ds("TCATBALF.KSDS"), "XREFFILE": r.ds("CARDXREF.KSDS"),
        "ACCTFILE": r.ds("ACCTDATA.KSDS"), "DISCGRP": r.ds("DISCGRP.KSDS"),
        "TRANSACT": r.ds("SYSTRAN.PS")}, parm=INTCALC_PARM)


def combtran(r: Runner):
    print("COMBTRAN  (SORT + REPRO - merge interest into transaction master)")
    r.repro_unload("COMBTRAN.STEP00.REPRO", "TRANSACT", r.ds("TRANSACT.KSDS"), r.ds("TRANSACT.BKUP"))
    r.sort("COMBTRAN.STEP05R.SORT", [r.ds("TRANSACT.BKUP"), r.ds("SYSTRAN.PS")],
           r.ds("TRANSACT.COMBINED"), 350, key=lambda x: x[0:16])
    r.repro_load("COMBTRAN.STEP10.REPRO", "TRANSACT", r.ds("TRANSACT.COMBINED"), r.ds("TRANSACT.KSDS"))


def tranrept(r: Runner):
    print("TRANREPT  (SORT + CBTRN03C - transaction detail report)")
    start, end = TRANREPT_DATES
    r.repro_unload("TRANREPT.STEP05R.REPRO", "TRANSACT", r.ds("TRANSACT.KSDS"), r.ds("TRANSACT.BKUP2"))
    r.sort("TRANREPT.STEP05R.SORT", [r.ds("TRANSACT.BKUP2")], r.ds("TRANSACT.DALY"), 350,
           key=lambda x: x[262:278],
           include=lambda x: start.encode() <= x[304:314] <= end.encode())
    Path(r.ds("DATEPARM")).write_bytes(f"{start} {end}".ljust(80).encode())
    r.pgm("TRANREPT.STEP10R.CBTRN03C", "CBTRN03C", {
        "TRANFILE": r.ds("TRANSACT.DALY"), "CARDXREF": r.ds("CARDXREF.KSDS"),
        "TRANTYPE": r.ds("TRANTYPE.KSDS"), "TRANCATG": r.ds("TRANCATG.KSDS"),
        "DATEPARM": r.ds("DATEPARM"), "TRANREPT": r.ds("TRANREPT.RPT")})


def creastmt(r: Runner):
    print("CREASTMT  (SORT + CBSTM03A/CBSTM03B - account statements)")
    r.repro_unload("CREASTMT.STEP010.REPRO", "TRANSACT", r.ds("TRANSACT.KSDS"), r.ds("TRANSACT.BKUP3"))
    r.sort("CREASTMT.STEP010.SORT", [r.ds("TRANSACT.BKUP3")], r.ds("TRXFL.SEQ"), 350,
           key=lambda x: (x[262:278], x[0:16]),
           outrec=lambda x: x[262:278] + x[0:262] + x[278:328])
    # OUTREC builds a 328-byte record into a RECSZ(350) cluster; REPRO pads with blanks
    recs = records(Path(r.ds("TRXFL.SEQ")).read_bytes(), 328)
    Path(r.ds("TRXFL.SEQ")).write_bytes(b"".join(x.ljust(350, b" ") for x in recs))
    r.repro_load("CREASTMT.STEP020.REPRO", "TRXFL", r.ds("TRXFL.SEQ"), r.ds("TRXFL.KSDS"))
    r.pgm("CREASTMT.STEP040.CBSTM03A", "CBSTM03A", {
        "TRNXFILE": r.ds("TRXFL.KSDS"), "XREFFILE": r.ds("CARDXREF.KSDS"),
        "ACCTFILE": r.ds("ACCTDATA.KSDS"), "CUSTFILE": r.ds("CUSTDATA.KSDS"),
        "STMTFILE": r.ds("STATEMNT.PS"), "HTMLFILE": r.ds("STATEMNT.HTML")})


def export_outputs(r: Runner):
    """Unload every master file the stream touched so it can be golden-compared."""
    out = r.w / "out"
    out.mkdir(exist_ok=True)
    for name in ("ACCTDATA", "TCATBALF", "TRANSACT"):
        r.repro_unload(f"EXPORT.{name}", name, r.ds(f"{name}.KSDS"), str(out / f"{name}.dat"))
    codes = "".join(f"{s['step']:<40}{'ABEND=' if s['cc'].startswith('U') else 'RC='}{s['cc']}".ljust(80) for s in r.steps
                    if s["step"].count(".") == 2 and not s["step"].endswith(("SORT", "REPRO")))
    (out / "RETURN-CODES.txt").write_text(codes)
    for f, lrecl in (("DALYREJS.PS", 430), ("SYSTRAN.PS", 350), ("TRANREPT.RPT", 133),
                     ("STATEMNT.PS", 80), ("STATEMNT.HTML", 100)):
        src = Path(r.ds(f))
        if src.exists():
            shutil.copy(src, out / f)


JOBS = {"POSTTRAN": posttran, "INTCALC": intcalc, "COMBTRAN": combtran,
        "TRANREPT": tranrept, "CREASTMT": creastmt}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--workdir", required=True, type=Path)
    ap.add_argument("--data-dir", type=Path, help="directory of ASCII seed files overriding app/data/ASCII (scenario data)")
    ap.add_argument("--jobs", default=",".join(JOBS))
    ap.add_argument("--clock", default=DEFAULT_CLOCK)
    ap.add_argument("--trace", action="store_true", help="use the -ftraceall build and write paragraph traces")
    a = ap.parse_args(argv)
    if a.workdir.exists():
        shutil.rmtree(a.workdir)
    r = Runner(a.workdir, a.trace, a.clock)
    setup_files(r, a.data_dir)
    try:
        for j in a.jobs.split(","):
            JOBS[j](r)
    except StepFailed as e:
        print(f"STREAM ABENDED in {e.step} RC={e.rc} - remaining jobs not run")
    export_outputs(r)
    (a.workdir / "steps.json").write_text(json.dumps(r.steps, indent=1))
    print(f"outputs in {a.workdir / 'out'}")


if __name__ == "__main__":
    main()
