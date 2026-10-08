package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.TransactionCategory;
import com.carddemo.domain.model.TransactionCategoryId;

public class TransactionCategoryCodec implements RecordCodec<TransactionCategory> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVTRA04Y;
    }

    @Override
    public TransactionCategory decode(String record) {
        RecordReader r = layout().reader(record);
        TransactionCategory c = new TransactionCategory();
        c.setId(new TransactionCategoryId(r.text("TRAN-TYPE-CD"), r.intValue("TRAN-CAT-CD")));
        c.setDescription(r.text("TRAN-CAT-TYPE-DESC"));
        return c;
    }

    @Override
    public String encode(TransactionCategory c) {
        return layout().writer()
                .text("TRAN-TYPE-CD", c.getId().typeCode())
                .number("TRAN-CAT-CD", c.getId().categoryCode())
                .text("TRAN-CAT-TYPE-DESC", c.getDescription())
                .toString();
    }
}
