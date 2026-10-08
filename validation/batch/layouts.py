"""Which copybook describes each golden artifact of the batch stream."""
from validation.lib.copybook import Field, layout

REJECT_TRAILER = [Field("WS-VALIDATION-FAIL-REASON", 350, 4, "9(04)"),
                  Field("WS-VALIDATION-FAIL-REASON-DESC", 354, 76, "X(76)")]

# artifact -> (lrecl, fields or None for report lines, key fields for record matching, producing step)
ARTIFACTS = {
    "TRANSACT.dat":  (350, lambda: layout("CVTRA05Y"), ("TRAN-ID",), "POSTTRAN/COMBTRAN  transaction master (KSDS unload)"),
    "DALYREJS.PS":   (430, lambda: layout("CVTRA06Y") + REJECT_TRAILER, ("DALYTRAN-ID",), "POSTTRAN  rejected daily transactions"),
    "ACCTDATA.dat":  (300, lambda: layout("CVACT01Y"), ("ACCT-ID",), "POSTTRAN/INTCALC  account master (KSDS unload)"),
    "TCATBALF.dat":  (50,  lambda: layout("CVTRA01Y"), ("TRANCAT-ACCT-ID", "TRANCAT-TYPE-CD", "TRANCAT-CD"), "POSTTRAN  category balances (KSDS unload)"),
    "SYSTRAN.PS":    (350, lambda: layout("CVTRA05Y"), ("TRAN-ID",), "INTCALC  generated interest transactions"),
    "RETURN-CODES.txt": (80, None, None, "JES  step condition codes (COBOL steps)"),
    "TRANREPT.RPT":  (133, None, None, "TRANREPT  daily transaction report"),
    "STATEMNT.PS":   (80,  None, None, "CREASTMT  customer statements (text)"),
    "STATEMNT.HTML": (100, None, None, "CREASTMT  customer statements (HTML)"),
}
