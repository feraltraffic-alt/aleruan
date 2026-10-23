package com.aleruan.dealos.evidence.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SummaryTextsTest {

    @Test
    void everyLanguageHasEveryText() {
        Set<String> english = SummaryTexts.of(DocLanguage.EN).stringPropertyNames();
        for (DocLanguage lang : DocLanguage.values()) {
            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(SummaryTexts.of(lang).stringPropertyNames());
            assertTrue(missing.isEmpty(), lang + " lacks " + missing);
            Set<String> extra = new TreeSet<>(SummaryTexts.of(lang).stringPropertyNames());
            extra.removeAll(english);
            assertTrue(extra.isEmpty(), lang + " has texts English lacks: " + extra);
        }
    }

    @Test
    void checkTextsFormatMoneyDatesStatusAndRole() {
        EvidenceCheck coverage = EvidenceCheck.warn("ACCEPTANCE_COVERAGE",
                Map.of("accepted", "125000.5", "contract", "300000", "currency", "CZK"));
        assertEquals("Zatím převzato 125\u00a0000,50 z 300\u00a0000,00 CZK", SummaryTexts.check(DocLanguage.CS, coverage));
        assertEquals("Accepted so far 125,000.50 of 300,000.00 CZK", SummaryTexts.check(DocLanguage.EN, coverage));

        EvidenceCheck flagged = EvidenceCheck.warn("COUNTERPARTY_FLAGGED",
                Map.of("name", "Stavby Novák s.r.o.", "role", "SUBCONTRACTOR", "status", "YELLOW", "date", "2026-05-04"));
        assertEquals("Stavby Novák s.r.o. (subdodavatel): kontrola v registrech žlutá dne 04.05.2026",
                SummaryTexts.check(DocLanguage.CS, flagged));
    }

    @Test
    void unknownCodesAndBadValuesDoNotBreakTheSummary() {
        assertEquals("check.SOMETHING_NEW.OK", SummaryTexts.check(DocLanguage.DE, EvidenceCheck.ok("SOMETHING_NEW", Map.of())));
        assertEquals("NEW_STATUS", SummaryTexts.status(DocLanguage.PL, "NEW_STATUS"));
        assertEquals("Site handed over on soon",
                SummaryTexts.check(DocLanguage.EN, EvidenceCheck.ok("HANDOVER", Map.of("date", "soon"))));
        assertEquals("Invoice $1 is overdue since ",
                SummaryTexts.check(DocLanguage.EN, EvidenceCheck.warn("INVOICE_OVERDUE", Map.of("number", "$1"))));
    }
}
