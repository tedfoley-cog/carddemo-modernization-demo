"""Deterministic mock-data scenarios for the batch stream.

    python3 -m validation.batch.scenarios edge   OUT_DIR
    python3 -m validation.batch.scenarios volume OUT_DIR [--accounts 5000] [--txns 100000]

`edge` = the shipped CardDemo data plus crafted records that drive every validation
branch of CBTRN02C (reject reasons 100-103, limit/expiry boundaries, credits, new
category balances) and the default-disclosure-group fallback in CBACT04C.
`abend-intcalc` = a posted type/category with no disclosure group -> INTCALC abends U0999.
`orphan-xref` = xref to a missing account -> reject 101, then CREASTMT abends.
`volume` = synthetic estate for throughput / NFR runs (seeded, reproducible).
Each scenario is a directory of ASCII seed files that overrides app/data/ASCII.
"""
import argparse
import random
import shutil
from decimal import Decimal
from pathlib import Path

from validation.lib.copybook import layout, record_dict, zoned_encode

REPO = Path(__file__).resolve().parents[2]
ASCII = REPO / "app" / "data" / "ASCII"


def build(fields, lrecl, **vals) -> str:
    rec = [" "] * lrecl
    for f in fields:
        if f.name in vals:
            v = vals[f.name]
            if f.numeric:
                digits = sum(1 for c in f.pic if c == "9") if "(" not in f.pic else None
                import re
                digits = sum(int(n) if n else 1 for _, n in re.findall(r"(9)(?:\((\d+)\))?", f.pic))
                s = zoned_encode(v, digits, f.scale, signed=f.pic.startswith("S"))
            else:
                s = str(v).ljust(f.length)[:f.length]
            rec[f.offset:f.offset + f.length] = list(s)
        elif f.numeric:
            import re
            digits = sum(int(n) if n else 1 for _, n in re.findall(r"(9)(?:\((\d+)\))?", f.pic))
            rec[f.offset:f.offset + f.length] = list(zoned_encode(0, digits, f.scale, signed=f.pic.startswith("S")))
    return "".join(rec)


def read(name, lrecl):
    return [l.rstrip("\r").ljust(lrecl) for l in (ASCII / name).read_text(encoding="latin-1").splitlines() if l.strip()]


ACCT, XREF, TRAN = layout("CVACT01Y"), layout("CVACT03Y"), layout("CVTRA06Y")
CARD, CUST = layout("CVACT02Y"), layout("CVCUS01Y")


def dalytran(tid, card, amt, ts, type_cd="01", cat=1, desc="Purchase", source="POS TERM"):
    return build(TRAN, 350, **{
        "DALYTRAN-ID": tid, "DALYTRAN-TYPE-CD": type_cd, "DALYTRAN-CAT-CD": cat, "DALYTRAN-SOURCE": source,
        "DALYTRAN-DESC": desc, "DALYTRAN-AMT": amt, "DALYTRAN-MERCHANT-ID": 800000001,
        "DALYTRAN-MERCHANT-NAME": "Scenario Merchant", "DALYTRAN-MERCHANT-CITY": "Dearborn",
        "DALYTRAN-MERCHANT-ZIP": "48120", "DALYTRAN-CARD-NUM": card, "DALYTRAN-ORIG-TS": ts + " 12:00:00.000000"})


def edge(out: Path):
    out.mkdir(parents=True, exist_ok=True)
    for f in ASCII.glob("*.txt"):
        shutil.copy(f, out / f.name)
    accts = {int(record_dict(ACCT, l.encode("latin-1"))["ACCT-ID"]): l for l in read("acctdata.txt", 300)}
    xrefs = read("cardxref.txt", 50)
    cards, custs = read("carddata.txt", 150), read("custdata.txt", 500)
    card0 = record_dict(XREF, xrefs[0].encode("latin-1"))["XREF-CARD-NUM"]

    # New account that expired on 2022-03-31, with card, xref and customer (copied from customer 1)
    exp_acct, exp_card, exp_cust = 99000000001, "9900000000000001", 990000001
    accts[exp_acct] = build(ACCT, 300, **{
        "ACCT-ID": exp_acct, "ACCT-ACTIVE-STATUS": "Y", "ACCT-CURR-BAL": Decimal("120.00"),
        "ACCT-CREDIT-LIMIT": Decimal("1000.00"), "ACCT-CASH-CREDIT-LIMIT": Decimal("200.00"),
        "ACCT-OPEN-DATE": "2019-04-01", "ACCT-EXPIRAION-DATE": "2022-03-31", "ACCT-REISSUE-DATE": "2021-04-01",
        "ACCT-ADDR-ZIP": "48120", "ACCT-GROUP-ID": "NOSUCHGRP"})
    xrefs.append(build(XREF, 50, **{"XREF-CARD-NUM": exp_card, "XREF-CUST-ID": exp_cust, "XREF-ACCT-ID": exp_acct}))
    cards.append(build(CARD, 150, **{"CARD-NUM": exp_card, "CARD-ACCT-ID": exp_acct, "CARD-CVV-CD": 123,
                                     "CARD-EMBOSSED-NAME": "EXPIRED SCENARIO", "CARD-EXPIRAION-DATE": "2022-03-31",
                                     "CARD-ACTIVE-STATUS": "Y"}))
    custs.append(f"{exp_cust:09d}" + custs[0][9:])

    # Order matters: CBTRN02C posts as it reads, so later checks see earlier postings.
    t = [
        dalytran("9900000000000101", "4000000000000000", Decimal("10.00"), "2022-06-30", desc="EDGE invalid card -> 100"),
        dalytran("9900000000000103", exp_card, Decimal("5.00"), "2022-03-31", desc="EDGE on expiry date -> accepted"),
        dalytran("9900000000000104", exp_card, Decimal("5.00"), "2022-04-01", desc="EDGE day after expiry -> 103"),
        dalytran("9900000000000105", exp_card, Decimal("995.01"), "2022-03-30", desc="EDGE one cent over limit -> 102"),
        dalytran("9900000000000106", exp_card, Decimal("995.00"), "2022-03-30", desc="EDGE exactly at limit -> accepted"),
        dalytran("9900000000000107", exp_card, Decimal("5000.00"), "2022-04-01", desc="EDGE over limit AND expired"),
        dalytran("9900000000000108", card0, Decimal("-75.25"), "2022-06-30", type_cd="03", desc="EDGE credit"),
        dalytran("9900000000000109", card0, Decimal("0.00"), "2022-06-30", desc="EDGE zero amount"),
        dalytran("9900000000000110", card0, Decimal("999999999.99"), "2022-06-30", desc="EDGE max amount -> 102"),
        dalytran("9900000000000111", card0, Decimal("1.11"), "2022-06-30", type_cd="02", cat=3,
                 desc="EDGE first type 02/0003 activity -> creates TCATBAL"),
    ]
    (out / "acctdata.txt").write_text("\n".join(accts.values()) + "\n", encoding="latin-1")
    (out / "cardxref.txt").write_text("\n".join(xrefs) + "\n", encoding="latin-1")
    (out / "carddata.txt").write_text("\n".join(cards) + "\n", encoding="latin-1")
    (out / "custdata.txt").write_text("\n".join(custs) + "\n", encoding="latin-1")
    (out / "dailytran.txt").write_text("\n".join(read("dailytran.txt", 350) + t) + "\n", encoding="latin-1")
    print(f"edge scenario: {len(t)} crafted transactions, +1 expired account -> {out}")


def _copy_base(out: Path):
    out.mkdir(parents=True, exist_ok=True)
    for f in ASCII.glob("*.txt"):
        shutil.copy(f, out / f.name)
    return record_dict(XREF, read("cardxref.txt", 50)[0].encode("latin-1"))["XREF-CARD-NUM"]


def abend_intcalc(out: Path):
    """Shipped data + one posted transaction whose type/category has no disclosure-group
    row (not even under DEFAULT). CBACT04C abends U0999 in 1200-A-GET-DEFAULT-DISCGRP."""
    card0 = _copy_base(out)
    t = [dalytran("9900000000000201", card0, Decimal("1.11"), "2022-06-30", type_cd="02", cat=9,
                  desc="ABEND type 02/0009 has no DISCGRP row")]
    (out / "dailytran.txt").write_text("\n".join(read("dailytran.txt", 350) + t) + "\n", encoding="latin-1")
    print(f"abend-intcalc scenario -> {out}")


def orphan_xref(out: Path):
    """A card cross-reference pointing at a non-existent account: CBTRN02C rejects its
    transaction with reason 101, then CBSTM03A abends when it reaches the orphan xref."""
    _copy_base(out)
    xrefs = read("cardxref.txt", 50) + [build(XREF, 50, **{
        "XREF-CARD-NUM": "9900000000000002", "XREF-CUST-ID": 1, "XREF-ACCT-ID": 99000000999})]
    t = [dalytran("9900000000000301", "9900000000000002", Decimal("10.00"), "2022-06-30",
                  desc="ORPHAN xref -> 101")]
    (out / "cardxref.txt").write_text("\n".join(xrefs) + "\n", encoding="latin-1")
    (out / "dailytran.txt").write_text("\n".join(read("dailytran.txt", 350) + t) + "\n", encoding="latin-1")
    print(f"orphan-xref scenario -> {out}")


def volume(out: Path, n_accts: int, n_txns: int, seed=20220706):
    rnd = random.Random(seed)
    out.mkdir(parents=True, exist_ok=True)
    for f in ("discgrp.txt", "trancatg.txt", "trantype.txt", "tcatbal.txt"):
        shutil.copy(ASCII / f, out / f)
    cust0 = read("custdata.txt", 500)[0]
    accts, xrefs, cards, custs, txns = [], [], [], [], []
    groups = ["A000000000", "DEFAULT", "ZEROAPR"]
    for i in range(1, n_accts + 1):
        acct, card, cust = 50000000000 + i, f"5{i:015d}", 500000000 + i
        accts.append(build(ACCT, 300, **{
            "ACCT-ID": acct, "ACCT-ACTIVE-STATUS": "Y", "ACCT-CURR-BAL": Decimal(rnd.randint(0, 500000)) / 100,
            "ACCT-CREDIT-LIMIT": Decimal(rnd.choice([2000, 5000, 10000, 25000])),
            "ACCT-CASH-CREDIT-LIMIT": Decimal(1000), "ACCT-OPEN-DATE": "2015-01-01",
            "ACCT-EXPIRAION-DATE": rnd.choice(["2025-12-31", "2026-06-30", "2022-05-31"]),
            "ACCT-REISSUE-DATE": "2020-01-01", "ACCT-ADDR-ZIP": "48120", "ACCT-GROUP-ID": rnd.choice(groups)}))
        xrefs.append(build(XREF, 50, **{"XREF-CARD-NUM": card, "XREF-CUST-ID": cust, "XREF-ACCT-ID": acct}))
        cards.append(build(CARD, 150, **{"CARD-NUM": card, "CARD-ACCT-ID": acct, "CARD-CVV-CD": 100 + i % 900,
                                         "CARD-EMBOSSED-NAME": f"VOLUME CUSTOMER {i}", "CARD-EXPIRAION-DATE": "2025-12-31",
                                         "CARD-ACTIVE-STATUS": "Y"}))
        custs.append(f"{cust:09d}" + cust0[9:])
    for j in range(1, n_txns + 1):
        i = rnd.randint(1, n_accts)
        card = f"5{i:015d}" if rnd.random() > 0.002 else f"4{j:015d}"
        typ, cat = rnd.choice([("01", 1), ("01", 2), ("02", 1), ("03", 1), ("04", 1)])
        amt = Decimal(rnd.randint(-20000, 150000)) / 100 if typ == "03" else Decimal(rnd.randint(100, 150000)) / 100
        txns.append(dalytran(f"7{j:015d}", card, amt, f"2022-06-{rnd.randint(1, 30):02d}", type_cd=typ, cat=cat))
    for name, rows in (("acctdata.txt", accts), ("cardxref.txt", xrefs), ("carddata.txt", cards),
                       ("custdata.txt", custs), ("dailytran.txt", txns)):
        (out / name).write_text("\n".join(rows) + "\n", encoding="latin-1")
    print(f"volume scenario: {n_accts} accounts, {n_txns} daily transactions -> {out}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("scenario", choices=["edge", "abend-intcalc", "orphan-xref", "volume"])
    ap.add_argument("out", type=Path)
    ap.add_argument("--accounts", type=int, default=5000)
    ap.add_argument("--txns", type=int, default=100000)
    a = ap.parse_args()
    if a.scenario == "volume":
        volume(a.out, a.accounts, a.txns)
    else:
        {"edge": edge, "abend-intcalc": abend_intcalc, "orphan-xref": orphan_xref}[a.scenario](a.out)
