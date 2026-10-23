package com.aleruan.dealos.invoice.domain;

import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.VatMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything printed on an invoice, frozen at issue time and stored next to the PDF. */
public record InvoiceDocument(
        InvoiceKind kind,
        String number,
        String creditOfNumber,
        String variableSymbol,
        DocLanguage language,
        LocalDate issueDate,
        LocalDate taxableDate,
        LocalDate dueDate,
        Party supplier,
        Party customer,
        String dealNumber,
        String subject,
        String currency,
        List<Line> lines,
        VatMode vatMode,
        BigDecimal vatRate,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal total,
        BigDecimal czkPerUnit,
        LocalDate rateDate,
        BigDecimal subtotalCzk,
        BigDecimal vatAmountCzk,
        String vatNote,
        String note,
        Bank bank,
        String paymentQr,
        String issuedBy) {

    public record Party(String name, String country, String companyId, String vatNumber, String address,
                        String email, String phone) {
    }

    public record Line(String description, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    public record Bank(String bankName, String iban, String swift) {
    }
}
