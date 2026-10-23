package com.aleruan.dealos.invoice.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.VatMode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceNumbersTest {

    @Test
    void numbersAndPaymentReferences() {
        assertEquals("FV-2026-0007", InvoiceKind.INVOICE.number(2026, 7));
        assertEquals("20260007", InvoiceKind.INVOICE.variableSymbol(2026, 7));
        assertEquals("OD-2026-0001", InvoiceKind.CREDIT_NOTE.number(2026, 1));
        assertEquals("920260001", InvoiceKind.CREDIT_NOTE.variableSymbol(2026, 1));
        assertEquals("202612345", InvoiceKind.INVOICE.variableSymbol(2026, 12345));
    }

    @Test
    void vatIsComputedOnTheBaseAndConvertedToCzk() {
        InvoiceMath.Amounts a = InvoiceMath.amounts(List.of(new BigDecimal("9840.00"), new BigDecimal("1617.00")),
                VatMode.STANDARD, new BigDecimal("21"));
        assertEquals(new BigDecimal("11457.00"), a.subtotal());
        assertEquals(new BigDecimal("2405.97"), a.vatAmount());
        assertEquals(new BigDecimal("58513.19"), InvoiceMath.vatInCzk(a.vatAmount(), new BigDecimal("24.32")));
        assertEquals(new BigDecimal("0.00"), InvoiceMath.amounts(List.of(BigDecimal.TEN), VatMode.REVERSE_CHARGE_CZ,
                new BigDecimal("21")).vatAmount());
        assertEquals(new BigDecimal("-1210.00"), InvoiceMath.amounts(List.of(new BigDecimal("-1000")), VatMode.STANDARD,
                new BigDecimal("21")).total());
    }

    @Test
    void labelsAreBilingualForForeignCustomers() {
        assertEquals("Datum splatnosti", InvoiceTexts.both(DocLanguage.CS, InvoiceTexts.Key.DUE));
        assertEquals("Datum splatnosti / Fällig am", InvoiceTexts.both(DocLanguage.DE, InvoiceTexts.Key.DUE));
        assertEquals("NIP", InvoiceTexts.companyIdLabel(DocLanguage.EN, "pl"));
    }
}
