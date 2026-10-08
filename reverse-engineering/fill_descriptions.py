"""Seed mftb descriptions.json from what the sources say about themselves (cited path:line)."""
import json, re, sys
from pathlib import Path

def program_line(path):
    lines = Path(path).read_text(errors="replace").splitlines()
    for i, l in enumerate(lines[:40], 1):
        m = re.search(r"\*\s*Function\s*:\s*(.+?)\s*\*?\s*$", l)
        if m:
            return m.group(1).strip(" .*"), f"{path.lstrip('./')}:{i}"
    return None, None

def jcl_line(path):
    lines = Path(path).read_text(errors="replace").splitlines()
    pgms = [(i, m.group(1)) for i, l in enumerate(lines, 1) if (m := re.search(r"EXEC\s+(?:PGM=)?([A-Z0-9#@$]+)", l)) and not l.startswith("//*")]
    if not pgms:
        return None, None
    names = []
    for _, p in pgms:
        if p not in names:
            names.append(p)
    first, last = pgms[0][0], pgms[-1][0]
    return f"runs {', '.join(names[:5])}{' ...' if len(names) > 5 else ''} ({len(pgms)} step{'s' if len(pgms) > 1 else ''})", f"{path.lstrip('./')}:{first}-{last}"

for mod in sys.argv[1:]:
    p = Path("store") / mod / "descriptions.json"
    d = json.loads(p.read_text())
    for e in d["descriptions"]:
        if e["description"] != "unknown":
            continue
        desc, cite = (program_line if e["kind"] == "program" else jcl_line)(e["path"])
        if desc:
            e.update(description=desc[0].upper() + desc[1:], basis=[cite], written_by="agent")
    p.write_text(json.dumps(d, indent=1))
    print(mod, sum(e["description"] != "unknown" for e in d["descriptions"]), "/", len(d["descriptions"]))
