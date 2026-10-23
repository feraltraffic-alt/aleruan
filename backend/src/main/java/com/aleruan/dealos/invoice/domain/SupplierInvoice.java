package com.aleruan.dealos.invoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** An invoice received from a subcontractor or supplier: registered, linked to a deal, marked paid. */
@Entity
@Table(name = "supplier_invoice")
public class SupplierInvoice {

    /** What the invoice says; amounts in its own currency. */
    public record Data(UUID supplierId, UUID dealId, String number, String variableSymbol, LocalDate issueDate,
                       LocalDate taxableDate, LocalDate dueDate, String currency, BigDecimal netAmount,
                       BigDecimal vatAmount, BigDecimal total, String vatNote, String bankAccount, String note) {
    }

    @Id
    private UUID id;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "variable_symbol")
    private String variableSymbol;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "taxable_date")
    private LocalDate taxableDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private String currency;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    @Column(name = "vat_amount")
    private BigDecimal vatAmount;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "vat_note")
    private String vatNote;

    @Column(name = "bank_account")
    private String bankAccount;

    /** Latest check of the bank account in the VAT registry (GREEN/YELLOW/RED), null if not checked. */
    @Column(name = "account_check")
    private String accountCheck;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "file_sha256")
    private String fileSha256;

    @Column(name = "import_id", updatable = false)
    private UUID importId;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Version
    private long version;

    protected SupplierInvoice() {
    }

    public SupplierInvoice(Data d, UUID importId, Instant createdAt, String createdBy) {
        this.id = UUID.randomUUID();
        this.supplierId = Objects.requireNonNull(d.supplierId(), "supplier");
        this.number = requireText(d.number(), "The invoice number is missing").strip();
        this.importId = importId;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        apply(d);
    }

    private void apply(Data d) {
        if (d.total() == null) {
            throw new IllegalArgumentException("The invoice total is missing");
        }
        String cur = requireText(d.currency(), "The currency is missing").strip().toUpperCase();
        if (!cur.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency is a three-letter code");
        }
        if (d.issueDate() != null && d.dueDate() != null && d.dueDate().isBefore(d.issueDate())) {
            throw new IllegalArgumentException("The due date is before the issue date");
        }
        this.dealId = d.dealId();
        this.variableSymbol = blank(d.variableSymbol());
        this.issueDate = d.issueDate();
        this.taxableDate = d.taxableDate();
        this.dueDate = d.dueDate();
        this.currency = cur;
        this.netAmount = d.netAmount();
        this.vatAmount = d.vatAmount();
        this.total = d.total();
        this.vatNote = blank(d.vatNote());
        this.bankAccount = blank(d.bankAccount());
        this.note = blank(d.note());
    }

    public void attachFile(UUID fileId, String sha256) {
        this.fileId = fileId;
        this.fileSha256 = sha256;
    }

    public void linkDeal(UUID dealId) {
        this.dealId = dealId;
    }

    public void markPaid(LocalDate on) {
        this.paidOn = on;
    }

    public void recordAccountCheck(String status) {
        this.accountCheck = status;
    }

    public boolean isOverdue(LocalDate today) {
        return paidOn == null && dueDate != null && dueDate.isBefore(today);
    }

    private static String requireText(String s, String message) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return s;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public UUID getId() { return id; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getDealId() { return dealId; }
    public String getNumber() { return number; }
    public String getVariableSymbol() { return variableSymbol; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getTaxableDate() { return taxableDate; }
    public LocalDate getDueDate() { return dueDate; }
    public String getCurrency() { return currency; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getTotal() { return total; }
    public String getVatNote() { return vatNote; }
    public String getBankAccount() { return bankAccount; }
    public String getAccountCheck() { return accountCheck; }
    public UUID getFileId() { return fileId; }
    public String getFileSha256() { return fileSha256; }
    public UUID getImportId() { return importId; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
