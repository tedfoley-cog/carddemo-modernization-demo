package com.carddemo.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** CVACT03Y CARD-XREF-RECORD. */
@Entity
@Table(name = "card_xref")
public class CardXref {
    @Id
    @Column(name = "xref_card_num")
    private String cardNum;
    @Column(name = "xref_cust_id")
    private Long custId;
    @Column(name = "xref_acct_id")
    private Long acctId;

    public String getCardNum() { return cardNum; }
    public void setCardNum(String v) { cardNum = v; }
    public Long getCustId() { return custId; }
    public void setCustId(Long v) { custId = v; }
    public Long getAcctId() { return acctId; }
    public void setAcctId(Long v) { acctId = v; }
}
