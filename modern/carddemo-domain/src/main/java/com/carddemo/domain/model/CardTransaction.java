package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Posted card transaction (copybook CVTRA05Y, VSAM TRANSACT + AIX on processing timestamp). */
@Entity
@Table(name = "card_transaction", indexes = {
        @Index(name = "ix_card_transaction_card", columnList = "card_number"),
        @Index(name = "ix_card_transaction_processed", columnList = "processed_at")})
public class CardTransaction extends AssignedIdEntity<String> {

    @Id
    @Column(name = "transaction_id", length = 16)
    private String transactionId;

    @Column(name = "type_code", length = 2, nullable = false)
    private String typeCode;

    @Column(name = "category_code", nullable = false)
    private Integer categoryCode;

    @Column(name = "source", length = 10)
    private String source;

    @Column(name = "description", length = 100)
    private String description;

    @Column(name = "amount", precision = 11, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "merchant_name", length = 50)
    private String merchantName;

    @Column(name = "merchant_city", length = 50)
    private String merchantCity;

    @Column(name = "merchant_zip", length = 10)
    private String merchantZip;

    @Column(name = "card_number", length = 16, nullable = false)
    private String cardNumber;

    @Column(name = "originated_at", nullable = false)
    private LocalDateTime originatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "originated_at_format", length = 3, nullable = false)
    private TimestampFormat originatedAtFormat;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Override
    public String getId() { return transactionId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Integer getCategoryCode() { return categoryCode; }
    public void setCategoryCode(Integer categoryCode) { this.categoryCode = categoryCode; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getMerchantCity() { return merchantCity; }
    public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
    public String getMerchantZip() { return merchantZip; }
    public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public LocalDateTime getOriginatedAt() { return originatedAt; }
    public void setOriginatedAt(LocalDateTime originatedAt) { this.originatedAt = originatedAt; }
    public TimestampFormat getOriginatedAtFormat() { return originatedAtFormat; }
    public void setOriginatedAtFormat(TimestampFormat originatedAtFormat) { this.originatedAtFormat = originatedAtFormat; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
