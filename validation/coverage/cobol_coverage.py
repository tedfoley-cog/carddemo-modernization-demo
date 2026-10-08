"""Legacy COBOL coverage from GnuCOBOL -ftraceall traces.

    python3 -m validation.coverage.cobol_coverage RUN_DIR [RUN_DIR...] [--report DIR]

For each program that ran, reports paragraph coverage and statement coverage
(executable statement lines in the PROCEDURE DIVISION hit by the trace) and lists
every paragraph that was never executed, so each gap is either closed with a new
scenario or explicitly justified (e.g. I/O-error abend handlers).
"""
import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
VERBS = set("""ACCEPT ADD ALTER CALL CANCEL CLOSE COMPUTE CONTINUE DELETE DISPLAY DIVIDE ELSE EVALUATE EXIT
GO GOBACK IF INITIALIZE INSPECT MOVE MULTIPLY OPEN PERFORM READ RETURN REWRITE SEARCH SET SORT START STOP
STRING SUBTRACT UNSTRING WHEN WRITE""".split())
TRACE_RE = re.compile(r"Program-Id:\s+(\S+)\s+(?:(Paragraph|Section|Entry):\s+(\S+)\s+)?(?:(\S+)\s+)?Line:\s+(\d+)")


def source_for(program: str) -> Path | None:
    for d in (REPO / "legacy-runtime" / "build" / "src", REPO / "app" / "cbl"):
        for ext in (".cbl", ".CBL"):
            p = d / f"{program}{ext}"
            if p.exists():
                return p
    return None


def static_model(src: Path):
    """Paragraph names (line) and executable statement lines in the procedure division."""
    paras, stmts, in_proc = {}, set(), False
    for n, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
        if len(line) < 8 or line[6] in "*/":
            continue
        code = line[7:72]
        if "PROCEDURE DIVISION" in code.upper():
            in_proc = True
            continue
        if not in_proc or not code.strip():
            continue
        if code[:4].strip() and re.match(r"^[A-Z0-9][A-Z0-9-]*\.\s*$", code.strip().upper()):
            paras[code.strip().rstrip(".").upper()] = n
            continue
        tok = code.split()[0].upper().rstrip(".")
        if tok in VERBS and tok != "ELSE":
            stmts.add(n)
    return paras, stmts


def analyse(run_dirs):
    hit_lines, hit_paras = {}, {}
    for d in run_dirs:
        for tf in Path(d).glob("log/*.trace"):
            src_prog = None
            for line in tf.open(errors="replace"):
                m = TRACE_RE.search(line)
                if not m:
                    continue
                prog, kind, name, _verb, ln = m.groups()
                hit_lines.setdefault(prog, set()).add(int(ln))
                if kind == "Paragraph":
                    hit_paras.setdefault(prog, set()).add(name.upper())
    out = []
    for prog in sorted(hit_lines):
        src = source_for(prog)
        if not src:
            continue
        paras, stmts = static_model(src)
        hp = hit_paras.get(prog, set()) & set(paras)
        hs = hit_lines[prog] & stmts
        out.append({
            "program": prog,
            "source": str(src.relative_to(REPO)),
            "paragraphs_total": len(paras), "paragraphs_hit": len(hp),
            "statements_total": len(stmts), "statements_hit": len(hs),
            "paragraph_pct": round(100 * len(hp) / len(paras), 1) if paras else 100.0,
            "statement_pct": round(100 * len(hs) / len(stmts), 1) if stmts else 100.0,
            "uncovered_paragraphs": sorted(paras.keys() - hp, key=lambda p: paras[p]),
            "uncovered_statement_lines": sorted(stmts - hs),
        })
    return out


def render_md(res):
    lines = ["# Legacy COBOL coverage (GnuCOBOL -ftraceall)\n",
             "| Program | Paragraphs | Statements | Uncovered paragraphs |", "|---|---:|---:|---|"]
    for r in res:
        lines.append(f"| {r['program']} | {r['paragraphs_hit']}/{r['paragraphs_total']} ({r['paragraph_pct']}%) | "
                     f"{r['statements_hit']}/{r['statements_total']} ({r['statement_pct']}%) | "
                     f"{', '.join(r['uncovered_paragraphs']) or '—'} |")
    return "\n".join(lines) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("runs", nargs="+", type=Path)
    ap.add_argument("--report", type=Path)
    a = ap.parse_args(argv)
    res = analyse(a.runs)
    md = render_md(res)
    print(md)
    if a.report:
        a.report.mkdir(parents=True, exist_ok=True)
        (a.report / "cobol-coverage.json").write_text(json.dumps(res, indent=1))
        (a.report / "cobol-coverage.md").write_text(md)
    return 0


if __name__ == "__main__":
    sys.exit(main())
