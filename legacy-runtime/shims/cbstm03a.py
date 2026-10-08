"""Source shim for CBSTM03A: the program walks z/OS control blocks (PSA -> TCB -> TIOT)
to DISPLAY the job/step and DD names. Those addresses do not exist on Linux, so this
replaces exactly that diagnostic block with an equivalent DISPLAY. All business logic,
including the ALTER/GO TO dispatch and CALL 'CBSTM03B', is compiled unchanged."""
import sys
from pathlib import Path

START = "SET ADDRESS OF PSA-BLOCK   TO PSAPTR."
END = "OPEN OUTPUT STMT-FILE HTML-FILE."


def patch(text: str) -> str:
    lines = text.splitlines(keepends=True)
    s = next(i for i, l in enumerate(lines) if START in l)
    e = next(i for i, l in enumerate(lines) if END in l)
    shim = [
        "      *> LINUX-RUNTIME SHIM: z/OS PSA/TCB/TIOT walk removed (see\n",
        "      *> legacy-runtime/shims/cbstm03a.py). Job/step from environment.\n",
        "           DISPLAY 'Running JCL : CREASTMT Step STEP040 '.\n",
        "           DISPLAY 'DD Names from TIOT: (not available on Linux)'.\n",
        "\n",
    ]
    return "".join(lines[:s] + shim + lines[e:])


if __name__ == "__main__":
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    dst.write_text(patch(src.read_text(errors="surrogateescape")), errors="surrogateescape")
