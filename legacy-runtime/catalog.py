"""Dataset catalog for the Linux legacy runtime.

Mirrors the VSAM/PS definitions in app/jcl (DEFINE CLUSTER KEYS/RECORDSIZE) and the
CICS FCT entries in app/csd/CARDDEMO.CSD. Every KSDS becomes a GnuCOBOL BDB indexed file.
"""
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
APP = REPO / "app"


@dataclass(frozen=True)
class Dataset:
    name: str            # logical name used by the harness
    dsn: str             # mainframe DSN it stands in for
    lrecl: int
    key: tuple | None    # (offset, length) 0-based, None for PS
    alt_keys: tuple = ()  # ((offset, length, duplicates), ...)
    ascii_src: str | None = None
    ebcdic_src: str | None = None
    extra: dict = field(default_factory=dict)


DATASETS = {d.name: d for d in [
    Dataset("ACCTDATA", "AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS", 300, (0, 11), ascii_src="acctdata.txt"),
    Dataset("CARDDATA", "AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS", 150, (0, 16), ((16, 11, True),), ascii_src="carddata.txt"),
    Dataset("CARDXREF", "AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS", 50, (0, 16), ((25, 11, True),), ascii_src="cardxref.txt"),
    Dataset("CUSTDATA", "AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS", 500, (0, 9), ascii_src="custdata.txt"),
    Dataset("DISCGRP", "AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS", 50, (0, 16), ascii_src="discgrp.txt"),
    Dataset("TCATBALF", "AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS", 50, (0, 17), ascii_src="tcatbal.txt"),
    Dataset("TRANCATG", "AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS", 60, (0, 6), ascii_src="trancatg.txt"),
    Dataset("TRANTYPE", "AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS", 60, (0, 2), ascii_src="trantype.txt"),
    Dataset("TRANSACT", "AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS", 350, (0, 16), ((304, 26, True),),
            ebcdic_src="AWS.M2.CARDDEMO.DALYTRAN.PS.INIT"),
    Dataset("USRSEC", "AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS", 80, (0, 8), ebcdic_src="AWS.M2.CARDDEMO.USRSEC.PS"),
    Dataset("TRXFL", "AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS", 350, (0, 32)),
    Dataset("DALYTRAN", "AWS.M2.CARDDEMO.DALYTRAN.PS", 350, None, ascii_src="dailytran.txt"),
]}


def fixed_records(ds: Dataset, src: Path | None = None) -> bytes:
    """Return the seed records of a dataset as RECFM=F bytes (ASCII, CR stripped, space padded)."""
    if src is None and ds.ascii_src:
        src = APP / "data" / "ASCII" / ds.ascii_src
    if src is not None:
        out = bytearray()
        for line in src.read_bytes().splitlines():
            line = line.rstrip(b"\r")
            if not line.strip():
                continue
            if len(line) > ds.lrecl:
                raise ValueError(f"{ds.name}: record longer than LRECL {ds.lrecl}: {len(line)}")
            out += line.ljust(ds.lrecl, b" ")
        return bytes(out)
    if ds.ebcdic_src:
        raw = (APP / "data" / "EBCDIC" / ds.ebcdic_src).read_bytes()
        if len(raw) % ds.lrecl:
            raise ValueError(f"{ds.name}: {len(raw)} bytes is not a multiple of LRECL {ds.lrecl}")
        return raw.decode("cp037").encode("latin-1")
    return b""


def records(data: bytes, lrecl: int) -> list[bytes]:
    return [data[i:i + lrecl] for i in range(0, len(data), lrecl)]
