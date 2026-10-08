package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.DisclosureGroup;
import com.carddemo.domain.model.DisclosureGroupId;

public class DisclosureGroupCodec implements RecordCodec<DisclosureGroup> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVTRA02Y;
    }

    @Override
    public DisclosureGroup decode(String record) {
        RecordReader r = layout().reader(record);
        DisclosureGroup g = new DisclosureGroup();
        g.setId(new DisclosureGroupId(r.text("DIS-ACCT-GROUP-ID"), r.text("DIS-TRAN-TYPE-CD"), r.intValue("DIS-TRAN-CAT-CD")));
        g.setInterestRate(r.decimal("DIS-INT-RATE"));
        return g;
    }

    @Override
    public String encode(DisclosureGroup g) {
        return layout().writer()
                .text("DIS-ACCT-GROUP-ID", g.getId().accountGroupId())
                .text("DIS-TRAN-TYPE-CD", g.getId().transactionTypeCode())
                .number("DIS-TRAN-CAT-CD", g.getId().transactionCategoryCode())
                .number("DIS-INT-RATE", g.getInterestRate())
                .toString();
    }
}
