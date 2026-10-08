package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.TransactionType;

public class TransactionTypeCodec implements RecordCodec<TransactionType> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVTRA03Y;
    }

    @Override
    public TransactionType decode(String record) {
        RecordReader r = layout().reader(record);
        TransactionType t = new TransactionType();
        t.setTypeCode(r.text("TRAN-TYPE"));
        t.setDescription(r.text("TRAN-TYPE-DESC"));
        return t;
    }

    @Override
    public String encode(TransactionType t) {
        return layout().writer().text("TRAN-TYPE", t.getTypeCode()).text("TRAN-TYPE-DESC", t.getDescription()).toString();
    }
}
