package com.carddemo.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** CVTRA05Y TRAN-RECORD. */
@Entity
@Table(name = "transaction")
public class Transaction {
    @Id
    @Column(name = "tran_id")
    private String tranId;
    @Column(name = "tran_type_cd")
    private String typeCd;
    @Column(name = "tran_cat_cd")
    private Integer catCd;
    @Column(name = "tran_source")
    private String source;
    @Column(name = "tran_desc")
    private String desc;
    @Column(name = "tran_amt")
    private BigDecimal amt;
    @Column(name = "tran_merchant_id")
    private Long merchantId;
    @Column(name = "tran_merchant_name")
    private String merchantName;
    @Column(name = "tran_merchant_city")
    private String merchantCity;
    @Column(name = "tran_merchant_zip")
    private String merchantZip;
    @Column(name = "tran_card_num")
    private String cardNum;
    @Column(name = "tran_orig_ts")
    private String origTs;
    @Column(name = "tran_proc_ts")
    private String procTs;

    public String getTranId() { return tranId; }
    public void setTranId(String v) { tranId = v; }
    public String getTypeCd() { return typeCd; }
    public void setTypeCd(String v) { typeCd = v; }
    public Integer getCatCd() { return catCd; }
    public void setCatCd(Integer v) { catCd = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public String getDesc() { return desc; }
    public void setDesc(String v) { desc = v; }
    public BigDecimal getAmt() { return amt; }
    public void setAmt(BigDecimal v) { amt = v; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long v) { merchantId = v; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String v) { merchantName = v; }
    public String getMerchantCity() { return merchantCity; }
    public void setMerchantCity(String v) { merchantCity = v; }
    public String getMerchantZip() { return merchantZip; }
    public void setMerchantZip(String v) { merchantZip = v; }
    public String getCardNum() { return cardNum; }
    public void setCardNum(String v) { cardNum = v; }
    public String getOrigTs() { return origTs; }
    public void setOrigTs(String v) { origTs = v; }
    public String getProcTs() { return procTs; }
    public void setProcTs(String v) { procTs = v; }
}
