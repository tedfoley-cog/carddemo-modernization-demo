package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;
import com.carddemo.domain.fixedwidth.RecordReader;
import com.carddemo.domain.model.Customer;
import java.math.BigDecimal;

public class CustomerCodec implements RecordCodec<Customer> {

    @Override
    public RecordLayout layout() {
        return Copybooks.CVCUS01Y;
    }

    @Override
    public Customer decode(String record) {
        RecordReader r = layout().reader(record);
        Customer c = new Customer();
        c.setId(r.longValue("CUST-ID"));
        c.setFirstName(r.text("CUST-FIRST-NAME"));
        c.setMiddleName(r.text("CUST-MIDDLE-NAME"));
        c.setLastName(r.text("CUST-LAST-NAME"));
        c.setAddressLine1(r.text("CUST-ADDR-LINE-1"));
        c.setAddressLine2(r.text("CUST-ADDR-LINE-2"));
        c.setAddressLine3(r.text("CUST-ADDR-LINE-3"));
        c.setStateCode(r.text("CUST-ADDR-STATE-CD"));
        c.setCountryCode(r.text("CUST-ADDR-COUNTRY-CD"));
        c.setZip(r.text("CUST-ADDR-ZIP"));
        c.setPhoneNumber1(r.text("CUST-PHONE-NUM-1"));
        c.setPhoneNumber2(r.text("CUST-PHONE-NUM-2"));
        c.setSsn(r.text("CUST-SSN"));
        c.setGovernmentIssuedId(r.text("CUST-GOVT-ISSUED-ID"));
        c.setDateOfBirth(r.date("CUST-DOB-YYYY-MM-DD"));
        c.setEftAccountId(r.text("CUST-EFT-ACCOUNT-ID"));
        c.setPrimaryCardHolder(r.text("CUST-PRI-CARD-HOLDER-IND"));
        c.setFicoCreditScore(r.intValue("CUST-FICO-CREDIT-SCORE"));
        return c;
    }

    @Override
    public String encode(Customer c) {
        return layout().writer()
                .number("CUST-ID", c.getId())
                .text("CUST-FIRST-NAME", c.getFirstName())
                .text("CUST-MIDDLE-NAME", c.getMiddleName())
                .text("CUST-LAST-NAME", c.getLastName())
                .text("CUST-ADDR-LINE-1", c.getAddressLine1())
                .text("CUST-ADDR-LINE-2", c.getAddressLine2())
                .text("CUST-ADDR-LINE-3", c.getAddressLine3())
                .text("CUST-ADDR-STATE-CD", c.getStateCode())
                .text("CUST-ADDR-COUNTRY-CD", c.getCountryCode())
                .text("CUST-ADDR-ZIP", c.getZip())
                .text("CUST-PHONE-NUM-1", c.getPhoneNumber1())
                .text("CUST-PHONE-NUM-2", c.getPhoneNumber2())
                .number("CUST-SSN", new BigDecimal(c.getSsn().isBlank() ? "0" : c.getSsn()))
                .text("CUST-GOVT-ISSUED-ID", c.getGovernmentIssuedId())
                .date("CUST-DOB-YYYY-MM-DD", c.getDateOfBirth())
                .text("CUST-EFT-ACCOUNT-ID", c.getEftAccountId())
                .text("CUST-PRI-CARD-HOLDER-IND", c.getPrimaryCardHolder())
                .number("CUST-FICO-CREDIT-SCORE", c.getFicoCreditScore())
                .toString();
    }
}
