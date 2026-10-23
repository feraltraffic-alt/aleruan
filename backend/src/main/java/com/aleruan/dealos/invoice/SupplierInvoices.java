package com.aleruan.dealos.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Public API of the invoice module for registering invoices received from suppliers (document import). */
public interface SupplierInvoices {

    record NewSupplierInvoice(UUID supplierId, UUID dealId, String number, String variableSymbol, LocalDate issueDate,
                              LocalDate taxableDate, LocalDate dueDate, String currency, BigDecimal netAmount,
                              BigDecimal vatAmount, BigDecimal total, String vatNote, String bankAccount,
                              String fileName, byte[] file, UUID importId) {
    }

    /** True when this supplier's invoice with this number is already registered. */
    boolean exists(UUID supplierId, String number);

    /** Registers the invoice and keeps the original file; returns its id. */
    UUID register(NewSupplierInvoice invoice);

    /** Stores the result of the bank account check (GREEN, YELLOW, RED or null). */
    void recordAccountCheck(UUID id, String status);
}
