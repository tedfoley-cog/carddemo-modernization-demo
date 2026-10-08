package com.carddemo.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/** CVACT01Y ACCOUNT-RECORD. */
@Entity
@Table(name = "account")
public class Account {
    @Id
    @Column(name = "acct_id")
    private Long acctId;
    @Column(name = "acct_active_status")
    private String activeStatus;
    @Column(name = "acct_curr_bal")
    private BigDecimal currBal;
    @Column(name = "acct_credit_limit")
    private BigDecimal creditLimit;
    @Column(name = "acct_cash_credit_limit")
    private BigDecimal cashCreditLimit;
    @Column(name = "acct_open_date")
    private String openDate;
    @Column(name = "acct_expiraion_date")
    private String expirationDate;
    @Column(name = "acct_reissue_date")
    private String reissueDate;
    @Column(name = "acct_curr_cyc_credit")
    private BigDecimal currCycCredit;
    @Column(name = "acct_curr_cyc_debit")
    private BigDecimal currCycDebit;
    @Column(name = "acct_addr_zip")
    private String addrZip;
    @Column(name = "acct_group_id")
    private String groupId;
    @Version
    private long version;

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long v) { acctId = v; }
    public String getActiveStatus() { return activeStatus; }
    public void setActiveStatus(String v) { activeStatus = v; }
    public BigDecimal getCurrBal() { return currBal; }
    public void setCurrBal(BigDecimal v) { currBal = v; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal v) { creditLimit = v; }
    public BigDecimal getCashCreditLimit() { return cashCreditLimit; }
    public void setCashCreditLimit(BigDecimal v) { cashCreditLimit = v; }
    public String getOpenDate() { return openDate; }
    public void setOpenDate(String v) { openDate = v; }
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String v) { expirationDate = v; }
    public String getReissueDate() { return reissueDate; }
    public void setReissueDate(String v) { reissueDate = v; }
    public BigDecimal getCurrCycCredit() { return currCycCredit; }
    public void setCurrCycCredit(BigDecimal v) { currCycCredit = v; }
    public BigDecimal getCurrCycDebit() { return currCycDebit; }
    public void setCurrCycDebit(BigDecimal v) { currCycDebit = v; }
    public String getAddrZip() { return addrZip; }
    public void setAddrZip(String v) { addrZip = v; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String v) { groupId = v; }
    public long getVersion() { return version; }
}
