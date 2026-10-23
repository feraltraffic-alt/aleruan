package com.aleruan.dealos.invoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Last used number of a series ("INVOICE-2026"); the row is locked while a number is taken. */
@Entity
@Table(name = "invoice_series")
public class InvoiceSeries {

    @Id
    private String id;

    @Column(nullable = false)
    private int last;

    protected InvoiceSeries() {
    }

    public static String id(InvoiceKind kind, int year) {
        return kind.name() + "-" + year;
    }

    public int next() {
        return ++last;
    }
}
