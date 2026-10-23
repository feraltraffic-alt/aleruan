package com.aleruan.dealos.quote.domain;

import com.aleruan.dealos.platform.documents.VatMode;

import com.aleruan.dealos.platform.documents.DocLanguage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything printed on a quote, frozen at issue time. Stored as JSON next to the PDF so the exact
 * content can be shown again without recomputing from a deal that has changed since.
 */
public record QuoteDocument(
        String number,
        int version,
        DocLanguage language,
        LocalDate issuedOn,
        LocalDate validUntil,
        String issuedBy,
        Party supplier,
        Party customer,
        String subject,
        String siteCountry,
        String currency,
        List<Line> lines,
        VatMode vatMode,
        BigDecimal vatRate,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal total,
        String vatNote,
        String terms,
        Bank bank,
        ChangeOrder changeOrder) {

    /** Present when the document is a change order sheet rather than a quote. */
    public record ChangeOrder(String reason, int extraDays) {
    }

    public boolean hasChangeOrder() {
        return changeOrder != null;
    }

    public record Party(String name, String country, String companyId, String vatNumber, String address,
                        String email, String phone, String web) {
    }

    public record Line(int position, String description, String unit, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal total) {
    }

    public record Bank(String bankName, String iban, String swift) {
    }

    public record Amounts(BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total) {
    }

    /** Subtotal from the (already rounded) line totals; VAT on the subtotal, half-up to cents. */
    public static Amounts amounts(List<Line> lines, VatMode mode, BigDecimal rate) {
        BigDecimal subtotal = lines.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = mode.chargesVat()
                ? subtotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new Amounts(subtotal, vat, subtotal.add(vat));
    }

    public String displayNumber() {
        return version > 1 ? number + "/" + version : number;
    }
}
