package com.aleruan.dealos.invoice.domain;

import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.VatMode;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** An invoice or credit note. Drafts are edited freely; issuing freezes the content (also enforced in the database). */
@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "deal_id", nullable = false, updatable = false)
    private UUID dealId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InvoiceKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    private String number;

    @Column(name = "variable_symbol")
    private String variableSymbol;

    @Column(name = "credit_of_id", updatable = false)
    private UUID creditOfId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocLanguage language;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_mode", nullable = false)
    private VatMode vatMode;

    @Column(name = "vat_rate", nullable = false)
    private BigDecimal vatRate;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "taxable_date", nullable = false)
    private LocalDate taxableDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "exchange_rate")
    private BigDecimal exchangeRate;

    @Column(name = "rate_date")
    private LocalDate rateDate;

    @Column(name = "vat_amount_czk")
    private BigDecimal vatAmountCzk;

    private String note;

    @Column(name = "paid_amount", nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    private String document;

    @Basic(fetch = FetchType.LAZY)
    private byte[] pdf;

    @Column(name = "pdf_sha256")
    private String pdfSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by")
    private String issuedBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
    }

    public record Settings(DocLanguage language, VatMode vatMode, BigDecimal vatRate, LocalDate taxableDate,
                           LocalDate dueDate, String note) {
    }

    public record LineData(String description, String unit, BigDecimal quantity, BigDecimal unitPrice, UUID stageId) {
    }

    public static Invoice draft(UUID dealId, UUID customerId, String currency, Settings s, List<LineData> lines,
                                Instant now, String actor) {
        Invoice i = new Invoice();
        i.id = UUID.randomUUID();
        i.dealId = dealId;
        i.customerId = customerId;
        i.kind = InvoiceKind.INVOICE;
        i.currency = currency;
        i.createdAt = now;
        i.createdBy = actor;
        i.edit(s, lines);
        return i;
    }

    /** A credit note reversing every line of an issued invoice. */
    public static Invoice creditNoteFor(Invoice original, String reason, LocalDate today, Instant now, String actor) {
        if (original.kind != InvoiceKind.INVOICE || !(original.status == InvoiceStatus.ISSUED || original.status == InvoiceStatus.PAID)) {
            throw new IllegalStateException("Only an issued invoice can be credited");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Say why the invoice is corrected");
        }
        Invoice c = new Invoice();
        c.id = UUID.randomUUID();
        c.dealId = original.dealId;
        c.customerId = original.customerId;
        c.kind = InvoiceKind.CREDIT_NOTE;
        c.creditOfId = original.id;
        c.currency = original.currency;
        c.createdAt = now;
        c.createdBy = actor;
        c.edit(new Settings(original.language, original.vatMode, original.vatRate, today, today, reason.strip()),
                original.lines.stream().map(l -> new LineData(l.getDescription(), l.getUnit(), l.getQuantity().negate(),
                        l.getUnitPrice(), l.getStageId())).toList());
        return c;
    }

    public void edit(Settings s, List<LineData> data) {
        requireDraft();
        if (s.vatRate() == null || s.vatRate().signum() < 0 || s.vatRate().compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        if (s.vatMode() != null && s.vatMode().chargesVat() && s.vatRate().signum() == 0) {
            throw new IllegalArgumentException("Czech VAT needs a rate; choose another VAT treatment for 0 %");
        }
        if (s.taxableDate() == null || s.dueDate() == null) {
            throw new IllegalArgumentException("Set the date of taxable supply and the due date");
        }
        if (s.dueDate().isBefore(s.taxableDate()) && kind == InvoiceKind.INVOICE) {
            throw new IllegalArgumentException("The due date cannot be before the date of taxable supply");
        }
        this.language = s.language() == null ? DocLanguage.CS : s.language();
        this.vatMode = s.vatMode();
        this.vatRate = s.vatMode().chargesVat() ? s.vatRate() : BigDecimal.ZERO;
        this.taxableDate = s.taxableDate();
        this.dueDate = s.dueDate();
        this.note = s.note() == null || s.note().isBlank() ? null : s.note().strip();
        lines.clear();
        int pos = 1;
        for (LineData l : data) {
            lines.add(new InvoiceLine(this, pos++, l.description(), l.unit(), l.quantity(), l.unitPrice(), l.stageId()));
        }
        InvoiceMath.Amounts a = InvoiceMath.amounts(lines.stream().map(InvoiceLine::getTotal).toList(), vatMode, vatRate);
        this.subtotal = a.subtotal();
        this.vatAmount = a.vatAmount();
        this.total = a.total();
    }

    /** Freezes the invoice: number, symbol, CZK VAT and the PDF exactly as sent. */
    public void issue(String number, String variableSymbol, LocalDate issueDate, BigDecimal czkPerUnit, LocalDate rateDate,
                      String documentJson, byte[] pdf, String pdfSha256, Instant now, String actor) {
        requireDraft();
        if (lines.isEmpty()) {
            throw new IllegalStateException("An invoice needs at least one line");
        }
        if (kind == InvoiceKind.INVOICE && total.signum() <= 0) {
            throw new IllegalStateException("An invoice total must be positive; use a credit note to reduce");
        }
        this.number = number;
        this.variableSymbol = variableSymbol;
        this.issueDate = issueDate;
        this.exchangeRate = czkPerUnit;
        this.rateDate = rateDate;
        this.vatAmountCzk = czkPerUnit == null ? null : InvoiceMath.vatInCzk(vatAmount, czkPerUnit);
        this.document = documentJson;
        this.pdf = pdf;
        this.pdfSha256 = pdfSha256;
        this.issuedAt = now;
        this.issuedBy = actor;
        this.status = InvoiceStatus.ISSUED;
    }

    /** Records a payment; the invoice is paid once the payments reach the total. */
    public void pay(BigDecimal amount, LocalDate on) {
        if (status != InvoiceStatus.ISSUED || kind != InvoiceKind.INVOICE) {
            throw new IllegalStateException("Payments are recorded on issued, unpaid invoices");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (on == null || (issueDate != null && on.isBefore(issueDate.minusDays(60)))) {
            throw new IllegalArgumentException("Give the payment date");
        }
        if (amount.compareTo(total.subtract(paidAmount)) > 0) {
            throw new IllegalArgumentException("The payment is more than the " + total.subtract(paidAmount)
                    + " " + currency + " still due; check the amount");
        }
        this.paidAmount = paidAmount.add(amount);
        this.paidOn = on;
        if (paidAmount.compareTo(total) >= 0) {
            this.status = InvoiceStatus.PAID;
        }
    }

    public void markCredited() {
        if (!(status == InvoiceStatus.ISSUED || status == InvoiceStatus.PAID)) {
            throw new IllegalStateException("Only an issued invoice can be credited");
        }
        this.status = InvoiceStatus.CREDITED;
    }

    public boolean isOverdue(LocalDate today) {
        return status == InvoiceStatus.ISSUED && kind == InvoiceKind.INVOICE && dueDate.isBefore(today);
    }

    public BigDecimal outstanding() {
        return status == InvoiceStatus.ISSUED && kind == InvoiceKind.INVOICE ? total.subtract(paidAmount) : BigDecimal.ZERO;
    }

    private void requireDraft() {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Invoice " + number + " is issued; correct it with a credit note");
        }
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public UUID getCustomerId() { return customerId; }
    public InvoiceKind getKind() { return kind; }
    public InvoiceStatus getStatus() { return status; }
    public String getNumber() { return number; }
    public String getVariableSymbol() { return variableSymbol; }
    public UUID getCreditOfId() { return creditOfId; }
    public DocLanguage getLanguage() { return language; }
    public String getCurrency() { return currency; }
    public VatMode getVatMode() { return vatMode; }
    public BigDecimal getVatRate() { return vatRate; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getTaxableDate() { return taxableDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public LocalDate getRateDate() { return rateDate; }
    public BigDecimal getVatAmountCzk() { return vatAmountCzk; }
    public String getNote() { return note; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getDocument() { return document; }
    public byte[] getPdf() { return pdf; }
    public String getPdfSha256() { return pdfSha256; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getIssuedBy() { return issuedBy; }
    public List<InvoiceLine> getLines() { return Collections.unmodifiableList(lines); }
}
