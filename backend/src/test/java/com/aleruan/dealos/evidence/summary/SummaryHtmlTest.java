package com.aleruan.dealos.evidence.summary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class SummaryHtmlTest {

    static SummaryModel model() {
        EvidenceEntry quote = EvidenceEntry.of(EvidenceSection.QUOTES, "Q-2026-0004/2").date(LocalDate.of(2026, 3, 2))
                .by("Lena Nováková").amount(new BigDecimal("482300"), "CZK").reference("Q-2026-0004/2")
                .file(EvidenceFile.pdf("Q-2026-0004-2.pdf", "aa", () -> new byte[0])).build();
        EvidenceEntry protocol = EvidenceEntry.of(EvidenceSection.ACCEPTANCE, "D-2026-0004-P1 Hrubá stavba")
                .date(LocalDate.of(2026, 6, 10)).by("Ing. Dvořák").status("ACCEPTED_WITH_REMARKS")
                .amount(new BigDecimal("200000"), "CZK").reference("D-2026-0004-P1")
                .text("Sokl dočistit.\nChybí revize <hromosvodu> & zábradlí")
                .file(EvidenceFile.pdf("D-2026-0004-P1.pdf", null, () -> new byte[0])).build();
        EvidenceEntry diary = EvidenceEntry.of(EvidenceSection.DIARY, "").date(LocalDate.of(2026, 6, 1)).by("Petr")
                .text("Betonáž stropu\u0007 nad 1. NP").build();
        SummaryModel.PackedFile q = new SummaryModel.PackedFile("02-quotes/Q-2026-0004-2.pdf", "a".repeat(64), 10, false);
        SummaryModel.PackedFile p = new SummaryModel.PackedFile("04-acceptance/D-2026-0004-P1.pdf", "b".repeat(64), 10, true);
        return new SummaryModel("D-2026-0004", "Rodinný dům Říčany", "Novák & syn s.r.o.", "Aleruan-industry s.r.o.",
                new BigDecimal("482300"), "CZK", LocalDateTime.of(2026, 10, 23, 14, 5), "Lena",
                List.of(EvidenceCheck.missing("CONTRACT_ATTACHED", Map.of()),
                        EvidenceCheck.warn("DEFECTS_OPEN", Map.of("count", "2")),
                        EvidenceCheck.ok("QUOTE_ISSUED", Map.of("number", "Q-2026-0004/2"))),
                List.of(new SummaryModel.Item(quote, List.of(q)), new SummaryModel.Item(protocol, List.of(p)),
                        new SummaryModel.Item(diary, List.of())),
                new SummaryModel.Audit(42, 3L, 118L, "c".repeat(64), true, 118, null), List.of(q, p));
    }

    @Test
    void isWellFormedXhtmlInEveryLanguage() throws Exception {
        for (DocLanguage lang : DocLanguage.values()) {
            String html = SummaryHtml.render(model(), lang, "data:image/png;base64,AA==", "Plex");
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
            assertFalse(html.contains("<hromosvodu>"), "user text is escaped");
            assertFalse(Pattern.compile("(check|section|status|level)\\.[A-Z]").matcher(html).find(),
                    "every check, section and status has a text in " + lang);
        }
    }

    @Test
    void listsChecksSectionsFilesAndTheAuditAnchor() {
        String html = SummaryHtml.render(model(), DocLanguage.CS, null, null);
        assertTrue(html.contains("1 z 3 v pořádku"));
        assertTrue(html.contains("Není přiložena podepsaná smlouva"));
        assertTrue(html.contains("převzato s výhradami"));
        assertTrue(html.contains("04-acceptance/D-2026-0004-P1.pdf *"));
        assertTrue(html.contains("b".repeat(64)));
        assertTrue(html.contains("c".repeat(64)));
        assertTrue(html.contains("sha256sum -c SHA256SUMS"));
    }
}
