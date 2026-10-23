package com.aleruan.dealos.docimport.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.docimport.reader.Extraction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImportChecksTest {

    private static final Set<String> OURS = Set.of("14220997", "CZ14220997");
    private static final Extraction.Party US = new Extraction.Party("Aleruan-industry s.r.o.", "142 20 997",
            "CZ 14220997", "Malešická 2855/2b, Praha 3", "CZ", "");
    private static final Extraction.Party CREW = new Extraction.Party("Stavby Horák s.r.o.", "68599528", "CZ68599528",
            "Vinohradská 112, Praha 3", "CZ", "123456789/0100");

    private static Extraction invoice(String type, Extraction.Party issuer, Extraction.Party recipient, String net,
                                      String vat, String total, List<Extraction.Line> lines, String issue, String due) {
        return new Extraction(type, "Invoice", "2026-0117", issue, issue, due, "CZK", "20260117", issuer, recipient,
                net, vat, total, "", "", lines, List.of());
    }

    private static Set<String> codes(List<ImportChecks.Warning> w) {
        return w.stream().map(ImportChecks.Warning::code).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void aConsistentSupplierInvoiceHasNoWarnings() {
        List<ImportChecks.Warning> w = ImportChecks.check(invoice("SUPPLIER_INVOICE", CREW, US, "10000.00", "2100.00",
                "12100.00", List.of(new Extraction.Line("Omítky", "100", "m2", "100.00", "10000.00", "21")),
                "2026-10-01", "2026-10-15"), OURS);
        assertTrue(w.isEmpty(), w.toString());
    }

    @Test
    void sumsDatesAndSidesAreChecked() {
        List<ImportChecks.Warning> w = ImportChecks.check(invoice("SUPPLIER_INVOICE", US, CREW, "10000.00", "2100.00",
                "12000.00", List.of(new Extraction.Line("Omítky", "100", "m2", "90.00", "9000.00", "21")),
                "2026-10-15", "2026-10-01"), OURS);
        assertEquals(Set.of("AMOUNTS_DONT_ADD_UP", "LINES_DONT_ADD_UP", "DUE_BEFORE_ISSUE", "WE_ARE_THE_ISSUER"), codes(w));
    }

    @Test
    void badValuesAndReaderNotesAreReported() {
        Extraction e = new Extraction("SUPPLIER_INVOICE", "", "", "31.13.2026", "", "", "", "", CREW, US, "abc", "",
                "", "", "", List.of(), List.of("Stamp covers the total"));
        Set<String> c = codes(ImportChecks.check(e, OURS));
        assertTrue(c.containsAll(Set.of("MISSING_NUMBER", "MISSING_TOTAL", "MISSING_CURRENCY", "BAD_DATE", "BAD_AMOUNT",
                "READER_NOTE")), c.toString());
    }

    @Test
    void amountsAndDatesInLocalFormats() {
        assertEquals(new BigDecimal("12345.50"), ImportChecks.parseAmount("12 345,50"));
        assertEquals(new BigDecimal("12345.50"), ImportChecks.parseAmount("12.345,50 Kč"));
        assertEquals(new BigDecimal("12345.50"), ImportChecks.parseAmount("12,345.5"));
        assertEquals(new BigDecimal("-1210.00"), ImportChecks.parseAmount("-1210"));
        assertNull(ImportChecks.parseAmount(""));
        assertNull(ImportChecks.parseAmount("n/a"));
        assertEquals(LocalDate.of(2026, 3, 4), ImportChecks.parseDate("4.3.2026"));
        assertEquals(LocalDate.of(2026, 3, 4), ImportChecks.parseDate("2026-03-04"));
        assertNull(ImportChecks.parseDate("March"));
    }

    @Test
    void theOtherSideIsWhoeverIsNotAleruan() {
        assertEquals("Stavby Horák s.r.o.", ImportChecks.otherSide(invoice("SUPPLIER_INVOICE", CREW, US, "", "", "",
                List.of(), "", ""), OURS).name());
        assertEquals("Stavby Horák s.r.o.", ImportChecks.otherSide(invoice("CUSTOMER_INVOICE", US, CREW, "", "", "",
                List.of(), "", ""), OURS).name());
        assertTrue(ImportChecks.isUs(US, OURS), "spaces in identifiers do not matter");
    }
}
