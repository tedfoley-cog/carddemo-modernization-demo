"""Minimal COBOL copybook layout parser (DISPLAY / COMP / COMP-3, OCCURS, REDEFINES).

Used to turn fixed-width records into named fields so golden-file diffs are
reported per field (e.g. ACCT-CURR-BAL) instead of per byte offset.
"""
import re
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
CPY = REPO / "app" / "cpy"


@dataclass
class Field:
    name: str
    offset: int
    length: int
    pic: str
    usage: str = "DISPLAY"

    @property
    def numeric(self):
        return "9" in self.pic and "X" not in self.pic

    @property
    def scale(self):
        m = re.search(r"V(9+)(?:\((\d+)\))?", self.pic)
        if not m:
            return 0
        return int(m.group(2)) if m.group(2) else len(m.group(1))

    def decode(self, rec: bytes):
        raw = rec[self.offset:self.offset + self.length]
        if not self.numeric or self.usage != "DISPLAY":
            return raw.decode("latin-1")
        return zoned_decode(raw, self.scale)


def _pic_len(pic: str) -> int:
    n = 0
    for ch, rep in re.findall(r"([X9AZ])(?:\((\d+)\))?", pic.replace("S", "").replace("V", "")):
        n += int(rep) if rep else 1
    return n


def _size(pic, usage):
    digits = _pic_len(pic)
    if usage in ("COMP", "COMP-4", "BINARY", "COMP-5"):
        return 2 if digits <= 4 else 4 if digits <= 9 else 8
    if usage == "COMP-3":
        return digits // 2 + 1
    return digits


OVERPUNCH_POS = "{ABCDEFGHI"
OVERPUNCH_NEG = "}JKLMNOPQR"


def zoned_decode(raw: bytes, scale: int):
    s = raw.decode("latin-1")
    if not s.strip():
        return None
    last = s[-1]
    sign = 1
    if last in OVERPUNCH_POS:
        s = s[:-1] + str(OVERPUNCH_POS.index(last))
    elif last in OVERPUNCH_NEG:
        s = s[:-1] + str(OVERPUNCH_NEG.index(last))
        sign = -1
    try:
        v = Decimal(int(s)) * sign
    except ValueError:
        return s  # not numeric: report raw
    return v.scaleb(-scale)


def zoned_encode(value, digits: int, scale: int, signed=True) -> str:
    q = int((Decimal(str(value)) * (10 ** scale)).to_integral_value())
    s = str(abs(q)).rjust(digits, "0")[-digits:]
    if not signed:
        return s
    table = OVERPUNCH_NEG if q < 0 else OVERPUNCH_POS
    return s[:-1] + table[int(s[-1])]


def parse(text: str, level01: str | None = None) -> list[Field]:
    stmts = []
    buf = ""
    for line in text.splitlines():
        if len(line) > 6 and line[6] in "*/":
            continue
        buf += " " + line[7:72]
    for st in buf.split("."):
        st = " ".join(st.split())
        if st:
            stmts.append(st)
    fields, offset = [], 0
    stack = []  # (level, start_offset, occurs, name)
    in_rec = level01 is None
    for st in stmts:
        toks = st.split()
        if not toks[0].isdigit():
            continue
        lvl, name = int(toks[0]), (toks[1] if len(toks) > 1 else "FILLER")
        if lvl == 88:
            continue
        if lvl == 1:
            in_rec = level01 is None or name == level01
            offset = 0
            stack = []
        if not in_rec:
            continue
        up = st.upper()
        if " REDEFINES " in f" {up} ":
            continue  # keep the primary layout only
        m = re.search(r"PIC(?:TURE)?\s+(\S+)", up)
        usage = "COMP-3" if "COMP-3" in up else "COMP" if re.search(r"\b(COMP|BINARY|COMP-4|COMP-5)\b", up) else "DISPLAY"
        occ = re.search(r"OCCURS\s+(\d+)", up)
        if m:
            pic = m.group(1)
            n = int(occ.group(1)) if occ else 1
            size = _size(pic, usage)
            for i in range(n):
                fields.append(Field(name if n == 1 else f"{name}({i+1})", offset, size, pic, usage))
                offset += size
    return fields


def layout(copybook: str, level01: str | None = None) -> list[Field]:
    return parse((CPY / f"{copybook}.cpy").read_text(errors="replace"), level01)


def record_dict(fields, rec: bytes) -> dict:
    return {f.name: f.decode(rec) for f in fields if f.name != "FILLER"}
