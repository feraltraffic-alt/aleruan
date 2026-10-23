package com.aleruan.dealos.quote.domain;

import com.aleruan.dealos.platform.documents.VatMode;

import com.aleruan.dealos.platform.documents.DocLanguage;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One issued version of a quote. Insert-only: the database refuses updates and deletes. */
@Entity
@Table(name = "quote")
public class Quote {

    @Id
    private UUID id;

    @Column(name = "deal_id", nullable = false, updatable = false)
    private UUID dealId;

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DocLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_mode", nullable = false, updatable = false)
    private VatMode vatMode;

    @Column(name = "vat_rate", nullable = false, updatable = false)
    private BigDecimal vatRate;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "vat_amount", nullable = false, updatable = false)
    private BigDecimal vatAmount;

    @Column(nullable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "issued_on", nullable = false, updatable = false)
    private LocalDate issuedOn;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDate validUntil;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "issued_by", updatable = false)
    private String issuedBy;

    @Column(nullable = false, updatable = false)
    private String document;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, updatable = false)
    private byte[] pdf;

    @Column(name = "pdf_sha256", nullable = false, updatable = false)
    private String pdfSha256;

    protected Quote() {
    }

    public Quote(UUID dealId, QuoteDocument d, Instant issuedAt, String documentJson, byte[] pdf, String pdfSha256) {
        this.id = UUID.randomUUID();
        this.dealId = dealId;
        this.number = d.number();
        this.version = d.version();
        this.language = d.language();
        this.vatMode = d.vatMode();
        this.vatRate = d.vatRate();
        this.currency = d.currency();
        this.subtotal = d.subtotal();
        this.vatAmount = d.vatAmount();
        this.total = d.total();
        this.issuedOn = d.issuedOn();
        this.validUntil = d.validUntil();
        this.issuedAt = issuedAt;
        this.issuedBy = d.issuedBy();
        this.document = documentJson;
        this.pdf = pdf;
        this.pdfSha256 = pdfSha256;
    }

    public String displayNumber() {
        return version > 1 ? number + "/" + version : number;
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public String getNumber() { return number; }
    public int getVersion() { return version; }
    public DocLanguage getLanguage() { return language; }
    public VatMode getVatMode() { return vatMode; }
    public BigDecimal getVatRate() { return vatRate; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getTotal() { return total; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public LocalDate getValidUntil() { return validUntil; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getIssuedBy() { return issuedBy; }
    public String getDocument() { return document; }
    public byte[] getPdf() { return pdf; }
    public String getPdfSha256() { return pdfSha256; }
}
