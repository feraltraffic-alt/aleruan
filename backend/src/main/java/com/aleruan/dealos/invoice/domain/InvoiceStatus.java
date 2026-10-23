package com.aleruan.dealos.invoice.domain;

/** Draft (editable, no number) → issued (number, PDF, frozen) → paid; an issued invoice can be credited. */
public enum InvoiceStatus {
    DRAFT, ISSUED, PAID, CREDITED;

    public boolean isIssued() {
        return this != DRAFT;
    }
}
