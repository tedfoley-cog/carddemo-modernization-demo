package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.Card;

public class CardCodec implements RecordCodec<Card> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVACT02Y;
    }

    @Override
    public Card decode(String record) {
        RecordReader r = layout().reader(record);
        Card c = new Card();
        c.setCardNumber(r.text("CARD-NUM"));
        c.setAccountId(r.longValue("CARD-ACCT-ID"));
        c.setCvvCode(r.intValue("CARD-CVV-CD"));
        c.setEmbossedName(r.text("CARD-EMBOSSED-NAME"));
        c.setExpirationDate(r.date("CARD-EXPIRAION-DATE"));
        c.setActiveStatus(r.text("CARD-ACTIVE-STATUS"));
        return c;
    }

    @Override
    public String encode(Card c) {
        return layout().writer()
                .text("CARD-NUM", c.getCardNumber())
                .number("CARD-ACCT-ID", c.getAccountId())
                .number("CARD-CVV-CD", c.getCvvCode())
                .text("CARD-EMBOSSED-NAME", c.getEmbossedName())
                .date("CARD-EXPIRAION-DATE", c.getExpirationDate())
                .text("CARD-ACTIVE-STATUS", c.getActiveStatus())
                .toString();
    }
}
