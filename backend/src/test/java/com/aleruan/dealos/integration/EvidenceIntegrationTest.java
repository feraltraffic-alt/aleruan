package com.aleruan.dealos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.deal.application.DealDocuments;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.evidence.application.EvidenceService;
import com.aleruan.dealos.invoice.application.InvoiceService;
import com.aleruan.dealos.invoice.domain.Invoice;
import com.aleruan.dealos.platform.audit.AuditRecord;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.VatMode;
import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.quote.application.QuoteService;
import com.aleruan.dealos.site.application.AcceptanceService;
import com.aleruan.dealos.site.application.SiteRecordsService;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.Defect;
import com.aleruan.dealos.site.domain.DefectSeverity;
import com.aleruan.dealos.site.domain.DiaryEntry;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.StageStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class EvidenceIntegrationTest extends IntegrationTestBase {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0};
    private static final byte[] PDF = "%PDF-1.4\n% smlouva o dilo\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired DealDocuments documents;
    @Autowired QuoteService quotes;
    @Autowired SiteService sites;
    @Autowired SiteRecordsService records;
    @Autowired AcceptanceService acceptance;
    @Autowired InvoiceService invoices;
    @Autowired EvidenceService evidence;
    @Autowired AuditService audit;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void completenessExportHashesAndAuditAnchor() throws Exception {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Rodina Říhová",
                "CZ", null, null, "Říčany", null, null, Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Deal deal = deals.create(new DealService.NewDeal("Rodinný dům Říčany", customer.getId(), Direction.CONSTRUCTION,
                "CZ", "CZK", new BigDecimal("20")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Hrubá stavba", "MASONRY", "kpl",
                BigDecimal.ONE, new BigDecimal("100000"), null, null, null));

        // nothing done yet: no quote is a gap; no contract is not yet expected before the customer agrees
        EvidenceService.Overview empty = evidence.overview(deal.getId());
        assertEquals(EvidenceCheck.Level.MISSING, level(empty, "QUOTE_ISSUED"));
        assertFalse(codes(empty).contains("CONTRACT_ATTACHED"));
        assertEquals(EvidenceCheck.Level.MISSING, level(empty, "COUNTERPARTY_UNCHECKED"));
        assertEquals(EvidenceCheck.Level.OK, level(empty, "AUDIT_CHAIN"));
        assertEquals(DocLanguage.CS, empty.deal().language());

        quotes.issue(deal.getId(), new QuoteService.IssueRequest(null, null, null, null, null));
        deals.moveTo(deal.getId(), DealStatus.WON);
        EvidenceService.Overview won = evidence.overview(deal.getId());
        assertEquals(EvidenceCheck.Level.OK, level(won, "QUOTE_ISSUED"));
        assertEquals(EvidenceCheck.Level.MISSING, level(won, "CONTRACT_ATTACHED"));
        assertEquals(EvidenceCheck.Level.WARN, level(won, "SITE_NOT_OPENED"));

        FileInfo contract = documents.attach(deal.getId(), "Smlouva o dílo – podepsaná.pdf", PDF);
        Site site = sites.create(deal.getId());
        AcceptanceStage stage = acceptance.create(site.getId(), new AcceptanceService.StageData("Hrubá stavba", null,
                new BigDecimal("60000"), null, Set.of()));
        acceptance.submit(stage.getId());
        acceptance.decide(stage.getId(), new AcceptanceService.Decision(StageStatus.ACCEPTED_WITH_REMARKS, "p. Říha",
                "Dočistit sokl", records.today().plusDays(7)));
        DiaryEntry entry = records.addEntry(site.getId(), new SiteRecordsService.DiaryData(records.today(), "Jasno", 5,
                "Betonáž stropu nad 1. NP", null));
        records.attach(SiteRecordsService.DIARY, entry.getId(), "IMG_1.jpg", JPEG);
        records.attach(SiteRecordsService.DIARY, entry.getId(), "IMG_1.jpg", JPEG); // same name twice
        records.report(site.getId(), new Defect.Details("Sokl", null, "sever", DefectSeverity.MINOR,
                null, stage.getId(), records.today(), null));

        Invoice advance = invoices.createDraft(deal.getId(), InvoiceService.Source.EMPTY, null);
        invoices.edit(advance.getId(), new Invoice.Settings(DocLanguage.CS, VatMode.STANDARD, new BigDecimal("21"),
                invoices.today(), invoices.today().plusDays(14), null), List.of(
                new Invoice.LineData("Záloha", null, BigDecimal.ONE, new BigDecimal("10000"), null)));
        Invoice issued = invoices.issue(advance.getId(), null);

        EvidenceService.Overview overview = evidence.overview(deal.getId());
        assertEquals(EvidenceCheck.Level.OK, level(overview, "CONTRACT_ATTACHED"));
        assertEquals(EvidenceCheck.Level.WARN, level(overview, "INVOICE_WITHOUT_ACCEPTANCE"));
        assertEquals(EvidenceCheck.Level.WARN, level(overview, "DEFECTS_OPEN"));
        assertEquals(EvidenceCheck.Level.WARN, level(overview, "ACCEPTANCE_COVERAGE"), "60 000 of 120 000 accepted");
        assertEquals(EvidenceCheck.Level.WARN, level(overview, "HANDOVER"));
        assertEquals(EvidenceCheck.Level.OK, level(overview, "DIARY_KEPT"));
        assertEquals(EvidenceCheck.Level.MISSING, overview.checks().get(0).level(), "gaps come first");
        assertTrue(overview.items().stream().anyMatch(i -> i.section() == EvidenceSection.INVOICES
                && i.title().equals(issued.getNumber())));
        assertTrue(overview.items().stream().flatMap(i -> i.files().stream())
                .anyMatch(f -> f.path().equals("01-contract/Smlouva_o_dilo_podepsana.pdf")
                        && f.sha256().equals(contract.sha256())));
        assertEquals(null, overview.lastExport());

        // export
        EvidenceService.Export export = evidence.export(deal.getId());
        Map<String, byte[]> zip = unzip(export);
        Files.deleteIfExists(export.file());
        assertTrue(export.fileName().startsWith("evidence-" + deal.getNumber() + "-"), export.fileName());
        assertTrue(zip.containsKey("01-contract/Smlouva_o_dilo_podepsana.pdf"), zip.keySet().toString());
        assertTrue(zip.keySet().stream().anyMatch(p -> p.startsWith("02-quotes/Q-") && p.endsWith(".pdf")));
        assertTrue(zip.containsKey("04-acceptance/" + deal.getNumber() + "-P1.pdf"));
        assertTrue(zip.containsKey("05-invoices/" + issued.getNumber() + ".pdf"));
        assertTrue(zip.containsKey("06-site-diary/" + records.today() + "-IMG_1.jpg"));
        assertTrue(zip.containsKey("06-site-diary/" + records.today() + "-IMG_1-2.jpg"));
        assertTrue(zip.containsKey("09-audit/audit-log.json"));
        assertTrue(zip.containsKey("summary-en.pdf") && zip.containsKey("summary-cs.pdf"));
        assertTrue(zip.containsKey("manifest.json") && zip.containsKey("SHA256SUMS"));
        String quotePath = zip.keySet().stream().filter(p -> p.startsWith("02-quotes/")).findFirst().orElseThrow();
        assertTrue(zip.get(quotePath).length > 1000, "the stored quote PDF itself");

        // every file is in SHA256SUMS with its real hash; SHA256SUMS lists nothing else
        String sums = new String(zip.get("SHA256SUMS"), StandardCharsets.UTF_8);
        Map<String, String> listed = new LinkedHashMap<>();
        for (String line : sums.split("\n")) {
            listed.put(line.substring(66), line.substring(0, 64));
        }
        assertEquals(zip.size() - 1, listed.size());
        listed.forEach((path, hash) -> assertEquals(sha256(zip.get(path)), hash, path));
        assertEquals(sha256(zip.get("SHA256SUMS")), export.sha256sums());

        // the hash of SHA256SUMS is anchored in the audit chain
        List<AuditRecord> history = audit.history("Deal", deal.getId().toString());
        AuditRecord last = history.get(history.size() - 1);
        assertEquals(EvidenceService.EXPORTED, last.action());
        assertTrue(last.payload().contains(export.sha256sums()));
        assertNotNull(evidence.overview(deal.getId()).lastExport());
        assertEquals(export.sha256sums(), evidence.overview(deal.getId()).lastExport().sha256sums());
        assertTrue(audit.verifyChain().valid());

        // the audit extract covers the deal's records: the quote, the stage decision, the defect, the uploads
        JsonNode extract = json.readTree(zip.get("09-audit/audit-log.json"));
        assertTrue(extract.size() > 10);
        Set<String> types = new java.util.HashSet<>();
        extract.forEach(r -> types.add(r.get("entityType").asText()));
        assertTrue(types.containsAll(Set.of("Deal", "Quote", "Site", "AcceptanceStage", "DiaryEntry", "Defect", "File",
                "Invoice", "Counterparty")), types.toString());
        extract.forEach(r -> assertTrue(r.has("hash") && r.has("prevHash")));

        JsonNode manifest = json.readTree(zip.get("manifest.json"));
        assertEquals(deal.getNumber(), manifest.path("deal").path("number").asText());
        assertTrue(manifest.path("checks").toString().contains("FILES_INTACT"));

        String summary = pdfText(zip.get("summary-cs.pdf"));
        assertTrue(summary.contains("Důkazní balíček"), summary);
        assertTrue(summary.contains(deal.getNumber()));
        assertTrue(summary.contains("Faktura " + issued.getNumber() + " nemá předávací protokol"), summary);
        assertTrue(pdfText(zip.get("summary-en.pdf")).contains("Evidence package"));

        // a stored file changed behind the system's back is reported, not silently packed
        jdbc.update("update stored_file set data = ? where id = ?", "%PDF-1.4\n% changed\n%%EOF\n".getBytes(StandardCharsets.US_ASCII),
                contract.id());
        EvidenceService.Export tampered = evidence.export(deal.getId());
        Map<String, byte[]> second = unzip(tampered);
        Files.deleteIfExists(tampered.file());
        String checks = json.readTree(second.get("manifest.json")).path("checks").toString();
        assertTrue(checks.contains("FILE_INTEGRITY") && checks.contains("01-contract/Smlouva_o_dilo_podepsana.pdf"), checks);
        assertFalse(checks.contains("FILES_INTACT"));
    }

    private static EvidenceCheck.Level level(EvidenceService.Overview o, String code) {
        return o.checks().stream().filter(c -> c.code().equals(code)).map(EvidenceCheck::level).findFirst().orElse(null);
    }

    private static Set<String> codes(EvidenceService.Overview o) {
        Set<String> s = new java.util.HashSet<>();
        o.checks().forEach(c -> s.add(c.code()));
        return s;
    }

    private static Map<String, byte[]> unzip(EvidenceService.Export export) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(export.file()); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                files.put(e.getName(), out.toByteArray());
            }
        }
        return files;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace(' ', ' ');
        }
    }
}
