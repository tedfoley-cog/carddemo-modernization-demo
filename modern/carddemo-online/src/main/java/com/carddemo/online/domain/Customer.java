package com.carddemo.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** CVCUS01Y CUSTOMER-RECORD. */
@Entity
@Table(name = "customer")
public class Customer {
    @Id
    @Column(name = "cust_id")
    private Long custId;
    @Column(name = "cust_first_name")
    private String firstName;
    @Column(name = "cust_middle_name")
    private String middleName;
    @Column(name = "cust_last_name")
    private String lastName;
    @Column(name = "cust_addr_line_1")
    private String addrLine1;
    @Column(name = "cust_addr_line_2")
    private String addrLine2;
    @Column(name = "cust_addr_line_3")
    private String addrLine3;
    @Column(name = "cust_addr_state_cd")
    private String stateCd;
    @Column(name = "cust_addr_country_cd")
    private String countryCd;
    @Column(name = "cust_addr_zip")
    private String zip;
    @Column(name = "cust_phone_num_1")
    private String phone1;
    @Column(name = "cust_phone_num_2")
    private String phone2;
    @Column(name = "cust_ssn")
    private Long ssn;
    @Column(name = "cust_govt_issued_id")
    private String govtIssuedId;
    @Column(name = "cust_dob_yyyy_mm_dd")
    private String dob;
    @Column(name = "cust_eft_account_id")
    private String eftAccountId;
    @Column(name = "cust_pri_card_holder_ind")
    private String priCardHolderInd;
    @Column(name = "cust_fico_credit_score")
    private Integer ficoScore;
    @Version
    private long version;

    public Long getCustId() { return custId; }
    public void setCustId(Long v) { custId = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { firstName = v; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String v) { middleName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { lastName = v; }
    public String getAddrLine1() { return addrLine1; }
    public void setAddrLine1(String v) { addrLine1 = v; }
    public String getAddrLine2() { return addrLine2; }
    public void setAddrLine2(String v) { addrLine2 = v; }
    public String getAddrLine3() { return addrLine3; }
    public void setAddrLine3(String v) { addrLine3 = v; }
    public String getStateCd() { return stateCd; }
    public void setStateCd(String v) { stateCd = v; }
    public String getCountryCd() { return countryCd; }
    public void setCountryCd(String v) { countryCd = v; }
    public String getZip() { return zip; }
    public void setZip(String v) { zip = v; }
    public String getPhone1() { return phone1; }
    public void setPhone1(String v) { phone1 = v; }
    public String getPhone2() { return phone2; }
    public void setPhone2(String v) { phone2 = v; }
    public Long getSsn() { return ssn; }
    public void setSsn(Long v) { ssn = v; }
    public String getGovtIssuedId() { return govtIssuedId; }
    public void setGovtIssuedId(String v) { govtIssuedId = v; }
    public String getDob() { return dob; }
    public void setDob(String v) { dob = v; }
    public String getEftAccountId() { return eftAccountId; }
    public void setEftAccountId(String v) { eftAccountId = v; }
    public String getPriCardHolderInd() { return priCardHolderInd; }
    public void setPriCardHolderInd(String v) { priCardHolderInd = v; }
    public Integer getFicoScore() { return ficoScore; }
    public void setFicoScore(Integer v) { ficoScore = v; }
    public long getVersion() { return version; }
}
