package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.Account;

public class AccountCodec implements RecordCodec<Account> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVACT01Y;
    }

    @Override
    public Account decode(String record) {
        RecordReader r = layout().reader(record);
        Account a = new Account();
        a.setId(r.longValue("ACCT-ID"));
        a.setActiveStatus(r.text("ACCT-ACTIVE-STATUS"));
        a.setCurrentBalance(r.decimal("ACCT-CURR-BAL"));
        a.setCreditLimit(r.decimal("ACCT-CREDIT-LIMIT"));
        a.setCashCreditLimit(r.decimal("ACCT-CASH-CREDIT-LIMIT"));
        a.setOpenDate(r.date("ACCT-OPEN-DATE"));
        a.setExpirationDate(r.date("ACCT-EXPIRAION-DATE"));
        a.setReissueDate(r.date("ACCT-REISSUE-DATE"));
        a.setCurrentCycleCredit(r.decimal("ACCT-CURR-CYC-CREDIT"));
        a.setCurrentCycleDebit(r.decimal("ACCT-CURR-CYC-DEBIT"));
        a.setAddressZip(r.text("ACCT-ADDR-ZIP"));
        a.setGroupId(r.text("ACCT-GROUP-ID"));
        return a;
    }

    @Override
    public String encode(Account a) {
        return layout().writer()
                .number("ACCT-ID", a.getId())
                .text("ACCT-ACTIVE-STATUS", a.getActiveStatus())
                .number("ACCT-CURR-BAL", a.getCurrentBalance())
                .number("ACCT-CREDIT-LIMIT", a.getCreditLimit())
                .number("ACCT-CASH-CREDIT-LIMIT", a.getCashCreditLimit())
                .date("ACCT-OPEN-DATE", a.getOpenDate())
                .date("ACCT-EXPIRAION-DATE", a.getExpirationDate())
                .date("ACCT-REISSUE-DATE", a.getReissueDate())
                .number("ACCT-CURR-CYC-CREDIT", a.getCurrentCycleCredit())
                .number("ACCT-CURR-CYC-DEBIT", a.getCurrentCycleDebit())
                .text("ACCT-ADDR-ZIP", a.getAddressZip())
                .text("ACCT-GROUP-ID", a.getGroupId())
                .toString();
    }
}
