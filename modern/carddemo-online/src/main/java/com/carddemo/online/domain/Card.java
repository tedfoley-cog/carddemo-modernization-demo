package com.carddemo.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** CVACT02Y CARD-RECORD. */
@Entity
@Table(name = "card")
public class Card {
    @Id
    @Column(name = "card_num")
    private String cardNum;
    @Column(name = "card_acct_id")
    private Long acctId;
    @Column(name = "card_cvv_cd")
    private Integer cvvCd;
    @Column(name = "card_embossed_name")
    private String embossedName;
    @Column(name = "card_expiraion_date")
    private String expirationDate;
    @Column(name = "card_active_status")
    private String activeStatus;
    @Version
    private long version;

    public String getCardNum() { return cardNum; }
    public void setCardNum(String v) { cardNum = v; }
    public Long getAcctId() { return acctId; }
    public void setAcctId(Long v) { acctId = v; }
    public Integer getCvvCd() { return cvvCd; }
    public void setCvvCd(Integer v) { cvvCd = v; }
    public String getEmbossedName() { return embossedName; }
    public void setEmbossedName(String v) { embossedName = v; }
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String v) { expirationDate = v; }
    public String getActiveStatus() { return activeStatus; }
    public void setActiveStatus(String v) { activeStatus = v; }
    public long getVersion() { return version; }
}
