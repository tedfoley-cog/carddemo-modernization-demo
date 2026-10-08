package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.CardXref;

public class CardXrefCodec implements RecordCodec<CardXref> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVACT03Y;
    }

    @Override
    public CardXref decode(String record) {
        RecordReader r = layout().reader(record);
        CardXref x = new CardXref();
        x.setCardNumber(r.text("XREF-CARD-NUM"));
        x.setCustomerId(r.longValue("XREF-CUST-ID"));
        x.setAccountId(r.longValue("XREF-ACCT-ID"));
        return x;
    }

    @Override
    public String encode(CardXref x) {
        return layout().writer()
                .text("XREF-CARD-NUM", x.getCardNumber())
                .number("XREF-CUST-ID", x.getCustomerId())
                .number("XREF-ACCT-ID", x.getAccountId())
                .toString();
    }
}
