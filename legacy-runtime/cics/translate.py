"""EXEC CICS translator for the Linux CICS runtime.

Does what the IBM CICS translator does, targeting our runtime instead of DFHEI1:
every `EXEC CICS ... END-EXEC` becomes `CALL 'CICSCMD' USING DFHEIBLK <command> <args>`,
`DFHRESP(cond)` becomes the numeric RESP value, the EIB is added to the LINKAGE SECTION and
`PROCEDURE DIVISION` receives `USING DFHEIBLK DFHCOMMAREA`. Program logic is untouched.

    python3 translate.py SRC.cbl OUT.cbl
"""
import re
import sys
from pathlib import Path

RESP = {"NORMAL": 0, "ERROR": 1, "RDATT": 2, "WRBRK": 3, "EOF": 4, "EODS": 5, "EOC": 6, "INBFMH": 7,
        "ENDINPT": 8, "NONVAL": 9, "NOSTART": 10, "TERMIDERR": 11, "FILENOTFOUND": 12, "NOTFND": 13,
        "DUPREC": 14, "DUPKEY": 15, "INVREQ": 16, "IOERR": 17, "NOSPACE": 18, "NOTOPEN": 19, "ENDFILE": 20,
        "ILLOGIC": 21, "LENGERR": 22, "QZERO": 23, "SIGNAL": 24, "QBUSY": 25, "ITEMERR": 26, "PGMIDERR": 27,
        "TRANSIDERR": 28, "ENDDATA": 29, "EXPIRED": 31, "MAPFAIL": 36, "INVMPSZ": 38, "QIDERR": 44,
        "DISABLED": 84, "NOTAUTH": 70}
ENDS_TASK = {"RETURN", "XCTL", "ABEND"}
AREA = "           "


def tokenize_options(body: str):
    """'SEND MAP('X') ERASE LENGTH(LENGTH OF Y)' -> [('SEND',None),('MAP',"'X'"),('ERASE',None),...]"""
    toks, i, n = [], 0, len(body)
    while i < n:
        if body[i].isspace():
            i += 1
            continue
        m = re.match(r"[A-Za-z0-9-]+", body[i:])
        if not m:
            raise ValueError(f"cannot parse EXEC CICS near: {body[i:i+30]!r}")
        word = m.group(0).upper()
        i += len(word)
        j = i
        while j < n and body[j].isspace():
            j += 1
        arg = None
        if j < n and body[j] == "(":
            depth, k, q = 0, j, None
            while k < n:
                c = body[k]
                if q:
                    if c == q:
                        q = None
                elif c in "'\"":
                    q = c
                elif c == "(":
                    depth += 1
                elif c == ")":
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            arg = " ".join(body[j + 1:k].split())
            i = k + 1
        toks.append((word, arg))
    return toks


def arg_clause(arg: str) -> str:
    a = arg.strip()
    if a.startswith(("'", '"')) or re.fullmatch(r"[+-]?\d+", a) or a.upper().startswith("LENGTH OF "):
        return f"BY CONTENT {a}"
    return f"BY REFERENCE {a}"


def literal_concat(s: str) -> list[str]:
    parts = [s[i:i + 40] for i in range(0, len(s), 40)] or [""]
    return [f"'{p}'" for p in parts]


def translate_exec(body: str) -> list[str]:
    toks = tokenize_options(body)
    verb = toks[0][0]
    names = {w for w, _ in toks}
    mapname = next((a for w, a in toks if w == "MAP" and a), None)
    if mapname and mapname.startswith("'"):  # symbolic map defaults, as the IBM translator does
        base = mapname.strip("'").strip()
        if verb == "RECEIVE" and "INTO" not in names and "SET" not in names:
            toks.append(("INTO", f"{base}I"))
        if verb == "SEND" and "FROM" not in names and "MAPONLY" not in names:
            toks.append(("FROM", f"{base}O"))
    spec, args = [verb], []
    for word, arg in toks[1:]:
        if verb == "HANDLE" and arg is not None:
            arg = f"'{arg.upper()}'"  # paragraph labels: recorded by the runtime, not branched to
        if arg is None:
            spec.append(word)
        else:
            args.append(arg)
            spec.append(f"{word}={len(args)}")
    lits = literal_concat(" ".join(spec))
    out = [f"{AREA}CALL 'CICSCMD' USING DFHEIBLK"]
    out.append(f"{AREA}     BY CONTENT {lits[0]}")
    out += [f"{AREA}       & {l}" for l in lits[1:]]
    out += [f"{AREA}     {arg_clause(a)}" for a in args]
    out.append(f"{AREA}END-CALL")
    if verb in ENDS_TASK:
        out.append(f"{AREA}GOBACK")
    return out


def fixed(line: str) -> tuple[str, str]:
    """split a fixed-format line into (indicator area 1-7, code 8-72)"""
    line = line.rstrip("\n").ljust(72)
    return line[:7], line[7:72]


def translate(text: str) -> str:
    out, buf, collecting, seen_linkage = [], [], False, False
    for raw in text.splitlines():
        ind, code = fixed(raw)
        if ind[6:7] in ("*", "/"):
            out.append(raw.rstrip()[:72])
            continue
        if collecting:
            if "END-EXEC" in code.upper():
                pre, post = re.split(r"END-EXEC", code, maxsplit=1, flags=re.I)
                buf.append(pre)
                out += translate_exec(" ".join(buf))
                post = post.strip()
                if post:
                    out.append(f"{AREA}{post}")
                collecting = False
            else:
                buf.append(code)
            continue
        m = re.search(r"\bEXEC\s+CICS\b", code, re.I)
        if m:
            before = code[:m.start()].rstrip()
            if before.strip():
                out.append(f"{ind}{before}")
            rest = code[m.end():]
            if "END-EXEC" in rest.upper():
                pre, post = re.split(r"END-EXEC", rest, maxsplit=1, flags=re.I)
                out += translate_exec(pre)
                if post.strip():
                    out.append(f"{AREA}{post.strip()}")
            else:
                buf, collecting = [rest], True
            continue
        code = re.sub(r"DFHRESP\s*\(\s*([A-Z0-9]+)\s*\)", lambda mm: str(RESP[mm.group(1).upper()]), code, flags=re.I)
        up = code.upper()
        if re.match(r"\s*LINKAGE\s+SECTION\s*\.", up):
            out.append(f"{ind}{code.rstrip()}")
            out.append(f"{AREA[:7]}COPY DFHEIBLK.")
            seen_linkage = True
            continue
        if re.match(r"\s*PROCEDURE\s+DIVISION\s*\.", up):
            if not seen_linkage:
                out.append(f"{AREA[:7]}LINKAGE SECTION.")
                out.append(f"{AREA[:7]}COPY DFHEIBLK.")
                out.append(f"{AREA[:7]}01  DFHCOMMAREA PIC X(1).")
            out.append(f"{AREA[:7]}PROCEDURE DIVISION USING DFHEIBLK DFHCOMMAREA.")
            continue
        out.append(f"{ind}{code}".rstrip())
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    dst.write_text(translate(src.read_text(errors="surrogateescape")), errors="surrogateescape")
