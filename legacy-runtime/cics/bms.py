"""BMS macro parser (DFHMSD/DFHMDI/DFHMDF) and symbolic-map layout.

Symbolic maps are generated with TIOAPFX=YES, EXTATT=YES (see app/cpy-bms): a 12-byte prefix,
then per named field L (S9(4) COMP), F/A, C, P, H, V (1 byte each) and the data.
"""
from dataclasses import dataclass, field
from pathlib import Path

PROT, NUM, BRT, DRK, MDT = 0x20, 0x10, 0x08, 0x0C, 0x01
COLORS = {"DEFAULT": 0, "BLUE": 0xF1, "RED": 0xF2, "PINK": 0xF3, "GREEN": 0xF4, "TURQUOISE": 0xF5,
          "YELLOW": 0xF6, "NEUTRAL": 0xF7}


@dataclass
class FieldDef:
    name: str | None
    row: int
    col: int  # 1-based position of the attribute byte; data starts at col + 1
    length: int
    attr: int
    color: int
    initial: str
    ic: bool


@dataclass
class MapDef:
    name: str
    mapset: str
    fields: list = field(default_factory=list)
    offsets: dict = field(default_factory=dict)
    size: int = 12


def _statements(text: str):
    stmt, quoted = None, False
    for line in text.splitlines():
        if line.startswith("*") and stmt is None:
            continue
        cont = len(line) > 71 and line[71] not in " "
        body = line[:71]
        if stmt is None:
            stmt = body
        else:
            seg = body[15:]
            stmt = stmt + seg if quoted else stmt.rstrip() + seg.lstrip()
        quoted = stmt.count("'") % 2 == 1
        if not cont:
            yield stmt.rstrip()
            stmt, quoted = None, False


def _operands(s: str) -> dict:
    out, cur, depth, q, i = [], "", 0, False, 0
    while i < len(s):
        c = s[i]
        if q:
            cur += c
            if c == "'":
                if i + 1 < len(s) and s[i + 1] == "'":
                    cur += "'"
                    i += 1
                else:
                    q = False
        elif c == "'":
            q, cur = True, cur + c
        elif c == "(":
            depth, cur = depth + 1, cur + c
        elif c == ")":
            depth, cur = depth - 1, cur + c
        elif c == "," and depth == 0:
            out.append(cur)
            cur = ""
        elif c == " " and depth == 0:
            break
        else:
            cur += c
        i += 1
    if cur:
        out.append(cur)
    ops = {}
    for o in out:
        k, _, v = o.partition("=")
        ops[k.strip().upper()] = v.strip()
    return ops


def _attr(spec: str | None) -> tuple[int, bool]:
    words = [w.strip() for w in (spec or "ASKIP").strip("()").split(",")]
    a = 0
    if "ASKIP" in words:
        a |= PROT | NUM
    elif "PROT" in words:
        a |= PROT
    if "NUM" in words:
        a |= NUM
    if "BRT" in words:
        a |= BRT
    if "DRK" in words:
        a |= DRK
    if "FSET" in words:
        a |= MDT
    return a, "IC" in words


def parse_mapset(path: Path) -> dict:
    maps, mapset, cur = {}, None, None
    for st in _statements(path.read_text(errors="replace")):
        if not st.strip() or st.lstrip().startswith("*"):
            continue
        label = st.split()[0] if not st.startswith(" ") else None
        rest = st[len(label):].strip() if label else st.strip()
        op, _, operands = rest.partition(" ")
        ops = _operands(operands.strip())
        if op == "DFHMSD" and ops.get("TYPE") != "FINAL":
            mapset = label
        elif op == "DFHMDI":
            cur = maps[label] = MapDef(label, mapset)
        elif op == "DFHMDF" and cur is not None:
            r, c = (int(x) for x in ops["POS"].strip("()").split(","))
            init = ops.get("INITIAL", "")
            if init.startswith("'"):
                init = init[1:-1].replace("''", "'")
            a, ic = _attr(ops.get("ATTRB"))
            f = FieldDef(label, r, c, int(ops.get("LENGTH", len(init) or 1)), a,
                         COLORS.get(ops.get("COLOR", "DEFAULT"), 0), init, ic)
            cur.fields.append(f)
            if label:
                cur.offsets[label] = cur.size
                cur.size += 7 + f.length
    return maps


def load_all(bms_dir: Path) -> dict:
    maps = {}
    for p in sorted(bms_dir.glob("*.bms")):
        maps.update(parse_mapset(p))
    return maps
