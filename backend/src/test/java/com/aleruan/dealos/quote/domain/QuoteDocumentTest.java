package com.aleruan.dealos.quote.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class QuoteDocumentTest {

    static final List<QuoteDocument.Line> LINES = List.of(
            new QuoteDocument.Line(1, "Demontáž", "m2", new BigDecimal("240"), new BigDecimal("11.88"), new BigDecimal("2851.20")),
            new QuoteDocument.Line(2, "Příčky <Q2> & tmel", "m2", new BigDecimal("180"), new BigDecimal("31.20"), new BigDecimal("5616.00")),
            new QuoteDocument.Line(3, "Elektro", "kpl", BigDecimal.ONE, new BigDecimal("8200"), new BigDecimal("8200.00")),
            new QuoteDocument.Line(4, "Materiál", "kpl", BigDecimal.ONE, new BigDecimal("3937.50"), new BigDecimal("3937.50")));

    static QuoteDocument document(QuoteLanguage lang, VatMode mode, BigDecimal rate, String customerCountry) {
        QuoteDocument.Amounts a = QuoteDocument.amounts(LINES, mode, rate);
        return new QuoteDocument("Q-2026-0001", 2, lang, LocalDate.of(2026, 10, 23), LocalDate.of(2026, 11, 22), "lena",
                new QuoteDocument.Party("Aleruan-industry s.r.o.", "CZ", "14220997", "CZ14220997", "Malešická 2855/2b, Praha 3",
                        null, null, null),
                new QuoteDocument.Party("Południe \"Budowa\" Sp. z o.o.", customerCountry, "2815901396", "PL2815901396",
                        "ul. Długa 5, Kraków", null, null, null),
                "Rekonstrukce kanceláří", "PL", "EUR", LINES, mode, rate, a.subtotal(), a.vatAmount(), a.total(),
                QuoteTexts.vatNote(lang, mode, VatAdvisor.Reason.SITE_IN_OTHER_EU_STATE), "Záloha 30 %\npřed zahájením",
                new QuoteDocument.Bank("PKO Bank Polski", "PL23102010130000020204812139", "BPKOPLPW"));
    }

    @Test
    void amountsRoundVatOnTheSubtotal() {
        QuoteDocument.Amounts a = QuoteDocument.amounts(LINES, VatMode.STANDARD, new BigDecimal("21"));
        assertEquals(new BigDecimal("20604.70"), a.subtotal());
        assertEquals(new BigDecimal("4326.99"), a.vatAmount());
        assertEquals(new BigDecimal("24931.69"), a.total());
        QuoteDocument.Amounts rc = QuoteDocument.amounts(LINES, VatMode.REVERSE_CHARGE_CZ, new BigDecimal("21"));
        assertEquals(new BigDecimal("0.00"), rc.vatAmount());
        assertEquals(new BigDecimal("20604.70"), rc.total());
    }

    @Test
    void htmlIsWellFormedEscapedAndLocalised() throws Exception {
        for (QuoteLanguage lang : QuoteLanguage.values()) {
            String html = QuoteHtml.render(document(lang, VatMode.REVERSE_CHARGE_EU, BigDecimal.ZERO, "PL"), null, "Plex");
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            assertNotNull(f.newDocumentBuilder().parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
            assertTrue(html.contains("Příčky &lt;Q2&gt; &amp; tmel"), lang.name());
            assertTrue(html.contains("Q-2026-0001/2"));
            assertTrue(html.contains("NIP 2815901396"), "Polish id is labelled NIP in " + lang);
            assertTrue(html.contains("IČO 14220997"), "Czech id stays IČO in " + lang);
            assertTrue(html.contains("194"), "reverse charge note cites the article");
            assertFalse(html.contains("<Q2>"));
        }
        String cs = QuoteHtml.render(document(QuoteLanguage.CS, VatMode.STANDARD, new BigDecimal("21"), "PL"), null, null);
        assertTrue(cs.contains("Cenová nabídka"));
        assertTrue(cs.contains("24 931,69 EUR"));
        assertTrue(cs.contains("DPH 21 %"));
        assertTrue(cs.contains("IBAN PL23 1020 1013 0000 0202 0481 2139"));
        assertTrue(cs.contains("Záloha 30 %<br/>před zahájením"));
        String en = QuoteHtml.render(document(QuoteLanguage.EN, VatMode.STANDARD, new BigDecimal("21"), "PL"), null, null);
        assertTrue(en.contains("24,931.69 EUR"));
        assertTrue(en.contains("23 Oct 2026"));
        String de = QuoteHtml.render(document(QuoteLanguage.DE, VatMode.STANDARD, new BigDecimal("21"), "PL"), null, null);
        assertTrue(de.contains("24.931,69 EUR"));
    }

    @Test
    void vatNotesExistForEveryLanguageExceptStandard() {
        for (QuoteLanguage lang : QuoteLanguage.values()) {
            assertNull(QuoteTexts.vatNote(lang, VatMode.STANDARD, null));
            assertTrue(QuoteTexts.vatNote(lang, VatMode.REVERSE_CHARGE_CZ, null).contains("92e"));
            assertTrue(QuoteTexts.vatNote(lang, VatMode.REVERSE_CHARGE_EU, null).contains("196"));
            assertNotNull(QuoteTexts.vatNote(lang, VatMode.OUTSIDE_SCOPE, null));
        }
        assertEquals(QuoteLanguage.CS, QuoteLanguage.forCountry("sk"));
        assertEquals(QuoteLanguage.DE, QuoteLanguage.forCountry("AT"));
        assertEquals(QuoteLanguage.EN, QuoteLanguage.forCountry("BE"));
        assertEquals(QuoteLanguage.EN, QuoteLanguage.forCountry(null));
    }
}
