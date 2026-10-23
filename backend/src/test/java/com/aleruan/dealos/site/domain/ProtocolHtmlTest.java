package com.aleruan.dealos.site.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.platform.documents.DocLanguage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class ProtocolHtmlTest {

    static ProtocolDocument doc(DocLanguage lang, StageStatus status, String remarks) {
        return new ProtocolDocument("D-2026-0003-P1", lang, LocalDate.of(2026, 11, 20),
                new ProtocolDocument.Party("Aleruan-industry s.r.o.", "CZ", "14220997", "CZ14220997", "Malešická 2855/2b, Praha 3"),
                new ProtocolDocument.Party("Montage Rhein GmbH", "DE", null, "DE811234567", "Industriestraße 4, Wesel"),
                "Fasáda, Wesel", "Industriestraße 4, Wesel, DE", "D-2026-0003", "Etapa 1: severní <strana> & lešení",
                "Zateplení\nvčetně soklu", List.of("Lešení", "Zateplení fasády ETICS"), new BigDecimal("8540"), "EUR",
                status, status == StageStatus.SUBMITTED ? null : "Herr Krüger", status == StageStatus.SUBMITTED ? null
                : LocalDate.of(2026, 11, 21), remarks, status == StageStatus.ACCEPTED_WITH_REMARKS ? LocalDate.of(2026, 11, 30)
                : null, "lena");
    }

    private static String render(ProtocolDocument d) throws Exception {
        String html = ProtocolHtml.render(d, null, null);
        assertNotNull(DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
        return html;
    }

    @Test
    void blankProtocolForTheCustomerToFillIn() throws Exception {
        String html = render(doc(DocLanguage.CS, StageStatus.SUBMITTED, null));
        assertTrue(html.contains("Předávací protokol"));
        assertTrue(html.contains("Etapa 1: severní &lt;strana&gt; &amp; lešení"));
        assertTrue(html.contains("Zateplení<br/>včetně soklu"));
        assertTrue(html.contains("8 540,00 EUR"));
        assertTrue(html.contains("class=\"blank\""));
        assertFalse(html.contains(">X<"), "no box ticked before the customer decides");
        assertTrue(html.contains("IČO 14220997"));
        assertTrue(html.contains("______________"));
    }

    @Test
    void decidedProtocolShowsTheChoiceRemarksAndDeadline() throws Exception {
        String html = render(doc(DocLanguage.DE, StageStatus.ACCEPTED_WITH_REMARKS, "Sockel an der Nordseite\nnachbessern"));
        assertTrue(html.contains("Abnahmeprotokoll"));
        assertTrue(html.contains(">X</span> abgenommen mit Vorbehalt"));
        assertTrue(html.contains("Sockel an der Nordseite<br/>nachbessern"));
        assertTrue(html.contains("30.11.2026"));
        assertTrue(html.contains("Herr Krüger"));
        assertFalse(html.contains("class=\"blank\""));
        for (DocLanguage lang : DocLanguage.values()) {
            assertTrue(render(doc(lang, StageStatus.REJECTED, "x")).contains(">X<"));
        }
    }
}
