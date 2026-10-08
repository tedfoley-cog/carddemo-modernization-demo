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
        return layout().writer()
                .number("TRANCAT-ACCT-ID", b.getId().accountId())
                .text("TRANCAT-TYPE-CD", b.getId().typeCode())
                .number("TRANCAT-CD", b.getId().categoryCode())
                .number("TRAN-CAT-BAL", b.getBalance())
                .toString();
    }
}
