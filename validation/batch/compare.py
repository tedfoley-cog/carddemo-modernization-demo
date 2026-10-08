"""Golden-file equivalence check: legacy COBOL batch outputs vs modernized outputs.

    python3 -m validation.batch.compare LEGACY_OUT MODERN_OUT [--report DIR]

Records are matched by the business key of their copybook (not by position), then
compared field by field using the copybook layout. Report artifacts are compared
line by line. Nothing is normalised: the modern side is expected to reproduce the
exact bytes, including run timestamps derived from the business clock.
Exit code 0 = functionally equivalent, 1 = mismatches, 2 = missing artifact.
"""
import argparse
import html
import json
import sys
from pathlib import Path

from validation.batch.layouts import ARTIFACTS
from validation.lib.copybook import record_dict

MAX_DIFFS = 25


def recs(path: Path, lrecl: int):
    data = path.read_bytes()
    if len(data) % lrecl:
        raise ValueError(f"{path}: size {len(data)} is not a multiple of LRECL {lrecl}")
    return [data[i:i + lrecl] for i in range(0, len(data), lrecl)]


def compare_records(name, lrecl, fields, keys, leg, mod):
    res = {"artifact": name, "legacy_records": len(leg), "modern_records": len(mod),
           "matched": 0, "mismatched": 0, "missing_in_modern": 0, "extra_in_modern": 0, "diffs": []}
    if fields is None:
        for i in range(max(len(leg), len(mod))):
            a = leg[i] if i < len(leg) else None
            b = mod[i] if i < len(mod) else None
            if a == b:
                res["matched"] += 1
                continue
            if a is None:
                res["extra_in_modern"] += 1
            elif b is None:
                res["missing_in_modern"] += 1
            else:
                res["mismatched"] += 1
            if len(res["diffs"]) < MAX_DIFFS:
                res["diffs"].append({"line": i + 1,
                                     "legacy": a.decode("latin-1").rstrip() if a else None,
                                     "modern": b.decode("latin-1").rstrip() if b else None})
        return res
    def keyed(rs):
        out = {}
        for r in rs:
            d = record_dict(fields, r)
            k = tuple(str(d[x]) for x in keys)
            out.setdefault(k, []).append((r, d))
        return out
    L, M = keyed(leg), keyed(mod)
    for k in list(L) + [k for k in M if k not in L]:
        la, ma = L.get(k, []), M.get(k, [])
        for i in range(max(len(la), len(ma))):
            a = la[i] if i < len(la) else None
            b = ma[i] if i < len(ma) else None
            if a and b and a[0] == b[0]:
                res["matched"] += 1
                continue
            if not b:
                res["missing_in_modern"] += 1
                diff = {"key": "/".join(k), "kind": "missing_in_modern"}
            elif not a:
                res["extra_in_modern"] += 1
                diff = {"key": "/".join(k), "kind": "extra_in_modern"}
            else:
                res["mismatched"] += 1
                fd = [{"field": f, "legacy": str(a[1][f]), "modern": str(b[1][f])}
                      for f in a[1] if a[1][f] != b[1][f]]
                if not fd:
                    fd = [{"field": "FILLER", "legacy": "(filler bytes differ)", "modern": ""}]
                diff = {"key": "/".join(k), "kind": "field_mismatch", "fields": fd}
            if len(res["diffs"]) < MAX_DIFFS:
                res["diffs"].append(diff)
    return res


def compare_dirs(legacy: Path, modern: Path):
    results = []
    for name, (lrecl, fields_fn, keys, desc) in ARTIFACTS.items():
        lp, mp = legacy / name, modern / name
        if not lp.exists():
            if mp.exists():
                results.append({"artifact": name, "description": desc, "status": "EXTRA",
                                "legacy_records": 0, "modern_records": len(recs(mp, lrecl))})
            continue
        if not mp.exists():
            results.append({"artifact": name, "description": desc, "status": "MISSING",
                            "legacy_records": len(recs(lp, lrecl)), "modern_records": 0})
            continue
        r = compare_records(name, lrecl, fields_fn() if fields_fn else None, keys,
                            recs(lp, lrecl), recs(mp, lrecl))
        r["description"] = desc
        r["status"] = "MATCH" if r["matched"] == r["legacy_records"] == r["modern_records"] else "DIFF"
        results.append(r)
    return results


def render_md(results, legacy, modern):
    lines = [f"# Batch golden-file parity\n", f"- legacy: `{legacy}`", f"- modern: `{modern}`\n",
             "| Artifact | Produced by | Legacy recs | Modern recs | Matched | Mismatched | Missing | Extra | Result |",
             "|---|---|---:|---:|---:|---:|---:|---:|---|"]
    for r in results:
        lines.append(f"| {r['artifact']} | {r['description']} | {r['legacy_records']} | {r['modern_records']} | "
                     f"{r.get('matched', 0)} | {r.get('mismatched', 0)} | {r.get('missing_in_modern', 0)} | "
                     f"{r.get('extra_in_modern', 0)} | **{r['status']}** |")
    for r in results:
        if r.get("diffs"):
            lines.append(f"\n## {r['artifact']} — first differences\n")
            for d in r["diffs"]:
                lines.append(f"- `{json.dumps(d)}`")
    return "\n".join(lines) + "\n"


def render_html(results, legacy, modern):
    ok = all(r["status"] == "MATCH" for r in results)
    rows = "".join(
        f"<tr class='{r['status'].lower()}'><td>{r['artifact']}</td><td>{html.escape(r['description'])}</td>"
        f"<td>{r['legacy_records']}</td><td>{r['modern_records']}</td><td>{r.get('matched', 0)}</td>"
        f"<td>{r.get('mismatched', 0)}</td><td>{r.get('missing_in_modern', 0)}</td><td>{r.get('extra_in_modern', 0)}</td>"
        f"<td><b>{r['status']}</b></td></tr>" for r in results)
    diffs = "".join(
        f"<h3>{r['artifact']}</h3><pre>{html.escape(chr(10).join(json.dumps(d, indent=1) for d in r['diffs']))}</pre>"
        for r in results if r.get("diffs"))
    return f"""<!doctype html><meta charset=utf-8><title>Batch parity</title>
<style>body{{font:14px system-ui;margin:2em}}table{{border-collapse:collapse}}td,th{{border:1px solid #ccc;padding:4px 8px}}
tr.match td:last-child{{color:#0a0}}tr.diff td:last-child,tr.missing td:last-child{{color:#c00}}pre{{background:#f6f6f6;padding:8px}}</style>
<h1>Batch golden-file parity: {'EQUIVALENT' if ok else 'NOT EQUIVALENT'}</h1>
<p>legacy: <code>{legacy}</code><br>modern: <code>{modern}</code></p>
<table><tr><th>Artifact</th><th>Produced by</th><th>Legacy</th><th>Modern</th><th>Matched</th><th>Mismatched</th><th>Missing</th><th>Extra</th><th>Result</th></tr>{rows}</table>
{diffs}"""


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("legacy", type=Path)
    ap.add_argument("modern", type=Path)
    ap.add_argument("--report", type=Path)
    a = ap.parse_args(argv)
    if not a.legacy.is_dir() or not a.modern.is_dir():
        print(f"compare: missing output directory: {a.legacy if not a.legacy.is_dir() else a.modern}", file=sys.stderr)
        return 2
    results = compare_dirs(a.legacy, a.modern)
    if not results:
        print(f"compare: no known artifacts in {a.legacy}; nothing was compared", file=sys.stderr)
        return 2
    md = render_md(results, a.legacy, a.modern)
    print(md)
    if a.report:
        a.report.mkdir(parents=True, exist_ok=True)
        (a.report / "batch-parity.json").write_text(json.dumps(results, indent=1, default=str))
        (a.report / "batch-parity.md").write_text(md)
        (a.report / "batch-parity.html").write_text(render_html(results, a.legacy, a.modern))
    if any(r["status"] in ("MISSING", "EXTRA") for r in results):
        return 2
    return 0 if all(r["status"] == "MATCH" for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
