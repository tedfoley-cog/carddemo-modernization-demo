package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.FixedWidthException;
import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.model.TimestampFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** CVTRA05Y codec, also used for the CVTRA06Y daily feed and the SYSTRAN dataset. */
public class CardTransactionCodec implements RecordCodec<CardTransaction> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVTRA05Y;
    }

    @Override
    public CardTransaction decode(String record) {
        RecordReader r = layout().reader(record);
        CardTransaction t = new CardTransaction();
        t.setTransactionId(r.text("TRAN-ID"));
        t.setTypeCode(r.text("TRAN-TYPE-CD"));
        t.setCategoryCode(r.intValue("TRAN-CAT-CD"));
        t.setSource(r.text("TRAN-SOURCE"));
        t.setDescription(r.text("TRAN-DESC"));
        t.setAmount(r.decimal("TRAN-AMT"));
        t.setMerchantId(r.longValue("TRAN-MERCHANT-ID"));
        t.setMerchantName(r.text("TRAN-MERCHANT-NAME"));
        t.setMerchantCity(r.text("TRAN-MERCHANT-CITY"));
        t.setMerchantZip(r.text("TRAN-MERCHANT-ZIP"));
        t.setCardNumber(r.text("TRAN-CARD-NUM"));
        String originated = r.text("TRAN-ORIG-TS");
        TimestampFormat format = TimestampFormat.detect(originated);
        t.setOriginatedAt(parse(format, originated, "TRAN-ORIG-TS"));
        t.setOriginatedAtFormat(format);
        String processed = r.text("TRAN-PROC-TS");
        t.setProcessedAt(processed.isBlank() ? null : parse(TimestampFormat.detect(processed), processed, "TRAN-PROC-TS"));
        return t;
    }

    @Override
    public String encode(CardTransaction t) {
        return layout().writer()
                .text("TRAN-ID", t.getTransactionId())
                .text("TRAN-TYPE-CD", t.getTypeCode())
                .number("TRAN-CAT-CD", t.getCategoryCode())
                .text("TRAN-SOURCE", t.getSource())
                .text("TRAN-DESC", t.getDescription())
                .number("TRAN-AMT", t.getAmount())
                .number("TRAN-MERCHANT-ID", t.getMerchantId())
                .text("TRAN-MERCHANT-NAME", t.getMerchantName())
                .text("TRAN-MERCHANT-CITY", t.getMerchantCity())
                .text("TRAN-MERCHANT-ZIP", t.getMerchantZip())
                .text("TRAN-CARD-NUM", t.getCardNumber())
                .text("TRAN-ORIG-TS", t.getOriginatedAtFormat().format(t.getOriginatedAt()))
                .text("TRAN-PROC-TS", t.getProcessedAt() == null ? "" : TimestampFormat.DB2.format(t.getProcessedAt()))
                .toString();
    }

    private static LocalDateTime parse(TimestampFormat format, String text, String field) {
        try {
            return format.parse(text);
        } catch (DateTimeParseException e) {
            throw new FixedWidthException("CVTRA05Y." + field + ": unsupported timestamp '" + text + "'", e);
        }
    }
}
