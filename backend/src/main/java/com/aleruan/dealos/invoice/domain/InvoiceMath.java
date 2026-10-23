package com.aleruan.dealos.invoice.domain;

import com.aleruan.dealos.platform.documents.VatMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Line totals, tax base, VAT and the CZK VAT required on foreign-currency tax documents. */
public final class InvoiceMath {

    public record Amounts(BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total) {
    }

    private InvoiceMath() {
    }

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static Amounts amounts(List<BigDecimal> lineTotals, VatMode mode, BigDecimal rate) {
        BigDecimal subtotal = lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = mode.chargesVat()
                ? subtotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new Amounts(subtotal, vat, subtotal.add(vat));
    }

    /** VAT in CZK: the VAT amount converted at the ČNB rate, rounded to hellers. */
    public static BigDecimal vatInCzk(BigDecimal vatAmount, BigDecimal czkPerUnit) {
        return vatAmount.multiply(czkPerUnit).setScale(2, RoundingMode.HALF_UP);
    }
}
