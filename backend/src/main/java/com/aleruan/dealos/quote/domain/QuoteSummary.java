package com.aleruan.dealos.quote.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A quote version without its PDF bytes, for lists. */
public record QuoteSummary(UUID id, String number, int version, QuoteLanguage language, VatMode vatMode,
                           BigDecimal vatRate, String currency, BigDecimal subtotal, BigDecimal vatAmount,
                           BigDecimal total, LocalDate issuedOn, LocalDate validUntil, Instant issuedAt,
                           String issuedBy, String pdfSha256) {

    public String displayNumber() {
        return version > 1 ? number + "/" + version : number;
    }
}
