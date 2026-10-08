package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.CategoryBalanceId;
import com.carddemo.domain.model.TransactionCategoryBalance;

public class CategoryBalanceCodec implements RecordCodec<TransactionCategoryBalance> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVTRA01Y;
    }

    @Override
    public TransactionCategoryBalance decode(String record) {
        RecordReader r = layout().reader(record);
        return new TransactionCategoryBalance(
                new CategoryBalanceId(r.longValue("TRANCAT-ACCT-ID"), r.text("TRANCAT-TYPE-CD"), r.intValue("TRANCAT-CD")),
                r.decimal("TRAN-CAT-BAL"));
    }

    @Override
    public String encode(TransactionCategoryBalance b) {
        String record = layout().writer()
                .number("TRANCAT-ACCT-ID", b.getId().accountId())
                .text("TRANCAT-TYPE-CD", b.getId().typeCode())
                .number("TRANCAT-CD", b.getId().categoryCode())
                .number("TRAN-CAT-BAL", b.getBalance())
                .toString();
        // TRAN-CAT-BAL-RECORD FILLER is zero-filled in the shipped data and kept by INITIALIZE in
        // CBTRN02C 2700-A-CREATE-TCATBAL-REC, so every TCATBALF record carries 22 zeros there.
        return record.substring(0, 28) + "0".repeat(22);
    }
}
