"""Linux CICS region for the original CardDemo online programs, with a browser 3270 terminal.

    python3 legacy-runtime/cics/region.py --workdir runs/online [--data-dir SCENARIO] [--port 3270]

Pseudo-conversational task flow, terminal control (BMS SEND/RECEIVE MAP, SEND TEXT) and
program control (RETURN TRANSID/COMMAREA, XCTL, ABEND, INQUIRE) live here; file control runs
inside each terminal's cicshost process against the same indexed files the batch stream uses.
"""
import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
RT = HERE.parent
ROOT = RT.parent
BUILD = RT / "build"
sys.path.insert(0, str(RT))
sys.path.insert(0, str(HERE))
from bms import BRT, DRK, MDT, NUM, PROT, load_all  # noqa: E402

AIDS = {"ENTER": 0x7D, "CLEAR": 0x6D, "PA1": 0x6C, "PA2": 0x6E, "PA3": 0x6B,
        **{f"PF{i}": v for i, v in zip(range(1, 13), [0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9,
                                                       0x7A, 0x7B, 0x7C])}}
RESP = {"NORMAL": 0, "INVREQ": 16, "PGMIDERR": 27, "MAPFAIL": 36}
EIB_LEN = 85


def pack(n: int, nbytes: int) -> bytes:
    digits = f"{abs(n):0{nbytes * 2 - 1}d}"[-(nbytes * 2 - 1):] + ("C" if n >= 0 else "D")
    return bytes.fromhex(digits)


def load_csd(path: Path) -> dict:
    text = path.read_text(errors="replace")
    out = {}
    for m in re.finditer(r"DEFINE TRANSACTION\((\w+)\)(.*?)(?=DEFINE |\Z)", text, re.S):
        p = re.search(r"PROGRAM\((\w+)\)", m.group(2))
        if p:
            out[m.group(1)] = p.group(1)
    return out


class Field:
    def __init__(self, fd, idx):
        self.idx, self.name, self.row, self.col, self.length = idx, fd.name, fd.row, fd.col, fd.length
        self.attr, self.color = fd.attr, fd.color
        self.value = fd.initial.ljust(fd.length)[:fd.length]
        self.mdt = bool(fd.attr & MDT)
        self.ic = fd.ic

    @property
    def prot(self):
        return bool(self.attr & PROT)

    def json(self):
        dark = (self.attr & DRK) == DRK
        return {"i": self.idx, "name": self.name, "row": self.row, "col": self.col + 1, "len": self.length,
                "prot": self.prot, "num": bool(self.attr & NUM), "dark": dark,
                "bright": (self.attr & DRK) == BRT, "color": self.color, "value": self.value, "mdt": self.mdt}


class Terminal:
    """One 3270 terminal: screen state + its task host process."""

    def __init__(self, region, termid):
        self.region, self.termid, self.lock = region, termid, threading.Lock()
        self.fields, self.map, self.text, self.cursor = [], None, [""] * 24, None
        self.next_transid, self.commarea, self.host = None, b"", None
        self.message, self.trace, self.taskn = "", [], 0
        self.text[0] = "CardDemo CICS region ready - type a transaction id (signon: CC00) and press Enter"

    # ---------- host process ----------
    def start_host(self):
        r_in, w_out = os.pipe()  # region -> host (fd 3)
        r_back, w_back = os.pipe()  # host -> region (fd 4)
        env = dict(self.region.env)
        log = open(self.region.logdir / f"{self.termid}.log", "ab")
        self.host = subprocess.Popen([str(BUILD / "cicshost")], env=env, stdout=log, stderr=subprocess.STDOUT,
                                     pass_fds=(3, 4), preexec_fn=lambda: (os.dup2(r_in, 3), os.dup2(w_back, 4)),
                                     cwd=self.region.workdir)
        os.close(r_in)
        os.close(w_back)
        self.to_host = os.fdopen(w_out, "w")
        self.from_host = os.fdopen(r_back, "r")

    def send(self, s):
        self.to_host.write(s)
        self.to_host.flush()

    # ---------- task execution ----------
    def eib(self, transid, aid, calen):
        now = self.region.now()
        e = bytearray(EIB_LEN)
        e[0:4] = pack(int(now.strftime("%H%M%S")), 4)
        e[4:8] = pack(int(f"1{now.strftime('%y')}{now.timetuple().tm_yday:03d}"), 4)
        e[8:12] = transid.ljust(4).encode()[:4]
        e[12:16] = pack(self.taskn, 4)
        e[16:20] = self.termid[-4:].encode()
        pos = 0 if not self.cursor else (self.cursor[0] - 1) * 80 + self.cursor[1] - 1
        e[22:24] = pos.to_bytes(2, "big")
        e[24:26] = calen.to_bytes(2, "big")
        e[26] = aid
        return e

    def run_transaction(self, transid, aid, commarea):
        program = self.region.csd.get(transid)
        if not program:
            self.unformatted([f"DFHAC2001 {self.region.now():%m/%d/%y %H:%M:%S} CARDDEMO Transaction '{transid}' "
                              "is not recognized. Check that the transaction name is correct."])
            self.next_transid = None
            return
        self.taskn += 1
        task = {"task": self.taskn, "transid": transid, "aid": aid, "programs": [], "commands": []}
        self.trace.append(task)
        self.next_transid, self.commarea, eibaid = None, b"", AIDS.get(aid, 0x7D)
        self.pending = (program, commarea)
        while self.pending:
            program, ca = self.pending
            self.pending = None
            task["programs"].append(program)
            if self.host is None or self.host.poll() is not None:
                self.start_host()
            self.ended = False
            self.send(f"TASK {program}\nEIB {self.eib(transid, eibaid, len(ca)).hex()}\nCOMMAREA {ca.hex()}\nGO\n")
            if not self.serve(task, transid):
                return

    def serve(self, task, transid):
        while True:
            line = self.from_host.readline()
            if not line:
                code = "ASRA"
                self.abend(task, transid, code, f"task host ended unexpectedly (see log {self.termid}.log)")
                self.host = None
                return False
            line = line.rstrip("\n")
            if line == "DONE":
                return True
            if line.startswith("ABEND "):
                parts = line.split(" ", 3)
                self.abend(task, transid, parts[1], f"unhandled RESP={parts[2]} on {parts[3] if len(parts) > 3 else ''}")
                self.host.wait()
                self.host = None
                return False
            if line.startswith("CMD "):
                spec = line[4:]
                eib, args = None, {}
                while True:
                    ln = self.from_host.readline().rstrip("\n")
                    if ln == "GO":
                        break
                    if ln.startswith("EIB "):
                        eib = bytearray.fromhex(ln[4:])
                    elif ln.startswith("ARG "):
                        _, i, size, hx = (ln.split(" ", 3) + [""])[:4]
                        args[int(i)] = bytes.fromhex(hx)
                    elif ln.startswith("NUM "):
                        _, i, v = ln.split(" ", 2)
                        args[int(i)] = int(v)
                task["commands"].append(spec.split()[0] + (" " + spec.split()[1] if spec.split()[0] in ("SEND", "RECEIVE") and len(spec.split()) > 1 and "=" not in spec.split()[1] else ""))
                reply = self.command(spec, eib, args, task, transid)
                self.send(reply + "OK\n")

    def command(self, spec, eib, args, task, transid):
        toks = spec.split()
        verb, opts, flags = toks[0], {}, set()
        for t in toks[1:]:
            if "=" in t:
                k, v = t.split("=")
                opts[k] = int(v)
            else:
                flags.add(t)
        arg = lambda k: args.get(opts[k]) if k in opts else None  # noqa: E731
        s = lambda k: (arg(k) or b"").decode("latin-1").strip() if isinstance(arg(k), (bytes, type(None))) else str(arg(k))  # noqa: E731
        out, resp = [], 0
        if verb == "SEND":
            if "MAP" in opts:
                self.send_map(s("MAPSET"), s("MAP"), arg("FROM"), flags)
            else:
                data = arg("FROM") or b""
                n = arg("LENGTH") if isinstance(arg("LENGTH"), int) else len(data)
                self.send_text(data[:n].decode("latin-1"), "ERASE" in flags)
        elif verb == "RECEIVE":
            buf, resp = self.receive_map(s("MAP"), task["aid"])
            if "INTO" in opts:
                out.append(f"ARG {opts['INTO']} {buf.hex()}\n")
        elif verb == "RETURN":
            if "TRANSID" in opts:
                self.next_transid = s("TRANSID")
                ca = arg("COMMAREA") or b""
                n = arg("LENGTH") if isinstance(arg("LENGTH"), int) else len(ca)
                self.commarea = ca[:n]
            else:
                self.next_transid, self.commarea = None, b""
        elif verb == "XCTL":
            prog = s("PROGRAM")
            ca = arg("COMMAREA") or b""
            n = arg("LENGTH") if isinstance(arg("LENGTH"), int) else len(ca)
            if self.region.installed(prog):
                self.pending = (prog, ca[:n])
            else:
                resp = RESP["PGMIDERR"]
                self.abend(task, transid, "APCT", f"XCTL to program {prog} which is not installed")
        elif verb == "INQUIRE":
            resp = 0 if self.region.installed(s("PROGRAM")) else RESP["PGMIDERR"]
        elif verb == "ABEND":
            self.abend(task, transid, s("ABCODE") or "????", "EXEC CICS ABEND issued by the program")
        elif verb == "ASSIGN":
            for k, v in (("APPLID", "CARDDEMO"), ("SYSID", "CDEM")):
                if k in opts:
                    out.append(f"ARG {opts[k]} {v.ljust(len(arg(k))).encode().hex()}\n")
        elif verb == "ASKTIME":
            ms = int((self.region.now() - dt.datetime(1900, 1, 1)).total_seconds() * 1000)
            out.append(f"NUM {opts['ABSTIME']} {ms}\n")
        elif verb == "FORMATTIME":
            t = dt.datetime(1900, 1, 1) + dt.timedelta(milliseconds=arg("ABSTIME"))
            ds, ts = s("DATESEP") or "", s("TIMESEP") or ""
            vals = {"YYYYMMDD": t.strftime(f"%Y{ds}%m{ds}%d"), "MMDDYY": t.strftime(f"%m{ds}%d{ds}%y"),
                    "YYMMDD": t.strftime(f"%y{ds}%m{ds}%d"), "TIME": t.strftime(f"%H{ts}%M{ts}%S")}
            for k, v in vals.items():
                if k in opts:
                    out.append(f"ARG {opts[k]} {v.ljust(len(arg(k))).encode().hex()}\n")
        elif verb == "WRITEQ":
            with open(self.region.logdir / f"TDQ-{s('QUEUE') or 'CSMT'}.log", "ab") as f:
                f.write((arg("FROM") or b"") + b"\n")
        elif verb in ("SYNCPOINT", "HANDLE"):
            pass  # no recoverable resources / label branching in this runtime (documented)
        else:
            resp = RESP["INVREQ"]
        eib[76:80] = resp.to_bytes(4, "big")
        out.insert(0, f"EIB {eib.hex()}\n")
        if "RESP" in opts:
            out.append(f"NUM {opts['RESP']} {resp}\n")
        if "RESP2" in opts:
            out.append(f"NUM {opts['RESP2']} 0\n")
        if resp and "RESP" not in opts and "NOHANDLE" not in flags and verb != "XCTL":
            return "".join(out) + "KILL\n"
        return "".join(out)

    # ---------- terminal control ----------
    def unformatted(self, lines):
        self.fields, self.map, self.cursor = [], None, (1, 1)
        self.text = [(lines[i] if i < len(lines) else "")[:80] for i in range(24)]

    def send_text(self, text, erase):
        lines = [text[i:i + 80] for i in range(0, len(text), 80)] or [""]
        self.unformatted(lines)

    def send_map(self, mapset, mapname, data, flags):
        md = self.region.maps[mapname]
        if "ERASE" in flags or self.map != mapname or not self.fields:
            self.fields = [Field(fd, i) for i, fd in enumerate(md.fields)]
            self.map, self.text = mapname, [""] * 24
        cursor_field = None
        if data and "MAPONLY" not in flags:
            for f in self.fields:
                if not f.name:
                    continue
                o = md.offsets[f.name]
                ln = int.from_bytes(data[o:o + 2], "big", signed=True)
                a, c, val = data[o + 2], data[o + 3], data[o + 7:o + 7 + f.length]
                if a:
                    f.attr, f.mdt = a, bool(a & MDT)
                if c:
                    f.color = c
                if val and val[0] != 0:
                    f.value = val.decode("latin-1").replace("\x00", " ").ljust(f.length)
                if ln == -1 and "CURSOR" in flags:
                    cursor_field = f
        if cursor_field is None:
            cursor_field = next((f for f in self.fields if f.ic), None) or next((f for f in self.fields if not f.prot), None)
        self.cursor = (cursor_field.row, cursor_field.col + 1) if cursor_field else (1, 1)

    def receive_map(self, mapname, aid):
        md = self.region.maps[mapname]
        buf = bytearray(md.size)
        modified = False
        for f in self.fields:
            if not f.name or f.name not in md.offsets:
                continue
            o = md.offsets[f.name]
            if f.mdt:
                v = f.value.rstrip(" \x00")
                modified = modified or True
                buf[o:o + 2] = len(v).to_bytes(2, "big")
                buf[o + 7:o + 7 + f.length] = v.ljust(f.length).encode("latin-1")[:f.length]
                if not v:
                    buf[o + 2] = 0x80
        if aid in ("CLEAR", "PA1", "PA2", "PA3") or not modified:
            return bytearray(md.size), RESP["MAPFAIL"]
        return buf, 0

    def abend(self, task, transid, code, why):
        task["abend"] = {"code": code, "why": why}
        self.unformatted([f"DFHAC2206 {self.region.now():%m/%d/%y %H:%M:%S} CARDDEMO Transaction {transid} "
                          f"abended with abend {code}.", "", f"({why})"])
        self.next_transid, self.commarea, self.pending = None, b"", None

    # ---------- terminal input ----------
    def key(self, aid, values, tran):
        with self.lock:
            for f in self.fields:
                if str(f.idx) in values and not f.prot:
                    v = values[str(f.idx)][:f.length]
                    if v.ljust(f.length) != f.value:
                        f.value, f.mdt = v.ljust(f.length), True
            if self.next_transid:
                self.run_transaction(self.next_transid, aid, self.commarea)
            elif aid == "CLEAR":
                self.unformatted([])
            else:
                t = (tran or "").strip().upper()[:4]
                if t:
                    self.run_transaction(t, aid, b"")
            return self.state()

    def state(self):
        return {"formatted": bool(self.fields), "map": self.map, "text": self.text,
                "fields": [f.json() for f in self.fields], "cursor": self.cursor,
                "next_transid": self.next_transid, "last_task": self.trace[-1] if self.trace else None}


class Region:
    def __init__(self, workdir: Path, data_dir: Path | None, clock: str, reuse: bool):
        from batch import Runner, setup_files  # same dataset build as the batch stream
        from catalog import DATASETS
        self.workdir = workdir
        self.logdir = workdir / "log"
        self.logdir.mkdir(parents=True, exist_ok=True)
        self.clock = dt.datetime.strptime(clock, "%Y/%m/%d %H:%M:%S") if clock else None
        r = Runner(workdir, False, clock or "")
        if not (reuse and Path(r.ds("ACCTDATA.KSDS")).exists()):
            setup_files(r, data_dir)
        self.maps = load_all(ROOT / "app" / "bms")
        self.csd = load_csd(ROOT / "app" / "csd" / "CARDDEMO.CSD")
        self.bin = BUILD / os.environ.get("CICS_BIN", "cics-bin")
        self.env = dict(os.environ, COB_LIBRARY_PATH=f"{self.bin}:{BUILD / 'cics-fh'}:{BUILD / 'bin'}",
                        CICS_FCT=str(BUILD / "cics-fh" / "fct.txt"), COB_FILE_PATH="")
        if clock:
            self.env["COB_CURRENT_DATE"] = clock
        for name, ds in DATASETS.items():
            if ds.key:
                self.env[f"DD_{name}"] = r.ds(f"{name}.KSDS")
        self.terms, self.lock = {}, threading.Lock()

    def now(self):
        return self.clock or dt.datetime.now().replace(microsecond=0)

    def installed(self, prog):
        return (self.bin / f"{prog.strip()}.so").exists()

    def terminal(self, tid=None):
        with self.lock:
            if tid and tid in self.terms:
                return self.terms[tid]
            tid = f"T{len(self.terms) + 1:03d}"
            self.terms[tid] = Terminal(self, tid)
            return self.terms[tid]


def serve(region: Region, port: int):
    page = (HERE / "terminal.html").read_bytes()

    class H(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def reply(self, obj, ctype="application/json"):
            body = obj if isinstance(obj, bytes) else json.dumps(obj).encode()
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path, _, q = self.path.partition("?")
            params = dict(p.split("=", 1) for p in q.split("&") if "=" in p)
            if path in ("/", "/index.html"):
                return self.reply(page, "text/html; charset=utf-8")
            t = region.terminal(params.get("id"))
            if path == "/api/screen":
                return self.reply({"id": t.termid, **t.state()})
            if path == "/api/trace":
                return self.reply({"id": t.termid, "tasks": t.trace})
            self.send_error(404)

        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            if self.path == "/api/session":
                t = region.terminal()
                return self.reply({"id": t.termid, **t.state()})
            if self.path == "/api/key":
                t = region.terminal(body.get("id"))
                return self.reply({"id": t.termid, **t.key(body.get("aid", "ENTER"), body.get("fields", {}),
                                                         body.get("tran"))})
            self.send_error(404)

    print(f"CardDemo CICS region on http://localhost:{port}  (data: {region.workdir})")
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--workdir", type=Path, default=ROOT / "runs" / "online")
    ap.add_argument("--data-dir", type=Path)
    ap.add_argument("--clock", default="2022/07/06 10:00:00", help="business clock ('' = wall clock)")
    ap.add_argument("--reuse", action="store_true", help="keep existing datasets in --workdir")
    ap.add_argument("--port", type=int, default=3270)
    a = ap.parse_args()
    serve(Region(a.workdir.resolve(), a.data_dir, a.clock, a.reuse), a.port)
