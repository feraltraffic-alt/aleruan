package com.aleruan.dealos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.deal.application.DealDocuments;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.docimport.application.ImportService;
import com.aleruan.dealos.docimport.domain.DocType;
import com.aleruan.dealos.docimport.domain.ImportDocument;
import com.aleruan.dealos.docimport.domain.ImportStatus;
import com.aleruan.dealos.evidence.application.EvidenceService;
import com.aleruan.dealos.invoice.application.SupplierInvoiceService;
import com.aleruan.dealos.invoice.domain.SupplierInvoice;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import com.aleruan.dealos.platform.web.ConflictException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportIntegrationTest extends IntegrationTestBase {

    @Autowired ImportService imports;
    @Autowired CounterpartyService counterparties;
    @Autowired CounterpartyLookup lookup;
    @Autowired DealService deals;
    @Autowired DealDocuments documents;
    @Autowired SupplierInvoiceService supplierInvoices;
    @Autowired EvidenceService evidence;
    @Autowired AuditService audit;

    private static byte[] pdf(String extractionJson) {
        return ("%PDF-1.4\n% " + extractionJson + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String invoice(String number, String supplierName, String ico) {
        return """
                {"documentType":"SUPPLIER_INVOICE","summary":"Invoice for plastering","documentNumber":"%s",
                "issueDate":"2026-10-01","taxableDate":"2026-10-01","dueDate":"2026-10-15","currency":"CZK",
                "variableSymbol":"20260117","issuer":{"name":"%s","companyId":"%s","vatNumber":"CZ%s",
                "address":"Kolín","country":"CZ","bankAccount":"123456789/0100"},"recipient":{"name":"Aleruan-industry s.r.o.",
                "companyId":"14220997","vatNumber":"CZ14220997","address":"Praha 3","country":"CZ","bankAccount":""},
                "netAmount":"10000.00","vatAmount":"2100.00","totalAmount":"12000.00","vatNote":"","references":"",
                "lines":[],"warnings":["Stamp over the total"]}""".formatted(number, supplierName, ico, ico).replace("\n", "");
    }

    private Deal wonDeal(String title) {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty(title + " zákazník", "SK",
                null, null, null, null, null, Set.of(CounterpartyRole.CUSTOMER)));
        Deal deal = deals.create(new DealService.NewDeal(title, customer.getId(), Direction.CONSTRUCTION, "CZ", "CZK",
                new BigDecimal("20")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Omítky", null, "m2", BigDecimal.TEN,
                new BigDecimal("100"), null, null, null));
        deals.moveTo(deal.getId(), DealStatus.QUOTED);
        deals.moveTo(deal.getId(), DealStatus.WON);
        return deal;
    }

    private ImportService.Review review(ImportService.Detail d, UUID dealId, UUID counterpartyId) {
        var e = d.read();
        var p = d.suggestion().party();
        return new ImportService.Review(d.suggestion().type(), e.documentNumber(), LocalDate.parse(e.issueDate()),
                LocalDate.parse(e.taxableDate()), LocalDate.parse(e.dueDate()), e.currency(), e.variableSymbol(),
                new BigDecimal(e.netAmount()), new BigDecimal(e.vatAmount()), new BigDecimal("12100.00"), e.vatNote(),
                new ImportService.PartyInput(p.name(), p.country(), p.companyId(), p.vatNumber(), p.address(),
                        p.bankAccount()),
                counterpartyId, null, dealId);
    }

    @Test
    void supplierInvoiceFromUploadToEvidence() {
        Deal deal = wonDeal("Byt Kolín import");
        String number = "FV-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] file = pdf(invoice(number, "Omítky Kolín import s.r.o.", "27082440"));

        ImportService.Upload up = imports.upload("faktura.pdf", file);
        assertFalse(up.duplicate());
        assertEquals(ImportStatus.QUEUED, up.document().getStatus());
        assertTrue(imports.upload("faktura-znovu.pdf", file).duplicate(), "the same file is not read twice");

        // read (the stub stands in for Claude); other tests may have queued documents, so read until ours is done
        for (int i = 0; i < 20 && imports.get(up.document().getId()).getStatus() == ImportStatus.QUEUED; i++) {
            imports.processNext();
        }
        ImportService.Detail d = imports.detail(up.document().getId());
        assertEquals(ImportStatus.EXTRACTED, d.document().getStatus());
        assertEquals(DocType.SUPPLIER_INVOICE, d.document().getDocType());
        assertEquals("stub", d.document().getModel());
        assertEquals("SUBCONTRACTOR", d.suggestion().role());
        assertEquals("Omítky Kolín import s.r.o.", d.suggestion().party().name());
        assertTrue(d.warnings().stream().anyMatch(w -> w.code().equals("AMOUNTS_DONT_ADD_UP")), "10 000 + 2 100 ≠ 12 000");
        assertTrue(d.warnings().stream().anyMatch(w -> w.code().equals("READER_NOTE")));

        assertThrows(IllegalArgumentException.class, () -> imports.confirm(up.document().getId(),
                new ImportService.Review(DocType.SUPPLIER_INVOICE, number, null, null, null, "CZK", null, null, null,
                        BigDecimal.ONE, null, null, null, null, deal.getId())), "an invoice needs its counterparty");

        // the reviewer fixes the total and confirms
        ImportService.Outcome out = imports.confirm(up.document().getId(), review(d, deal.getId(), null));
        assertEquals("SUPPLIER_INVOICE", out.resultType());
        assertTrue(out.counterpartyCreated());
        var supplier = lookup.find(out.counterpartyId()).orElseThrow();
        assertTrue(supplier.hasRole("SUBCONTRACTOR"));
        assertEquals("27082440", supplier.companyId());
        assertNotNull(supplier.checkStatus(), "checked in the registries after confirming");

        SupplierInvoice inv = supplierInvoices.get(out.resultId());
        assertEquals(number, inv.getNumber());
        assertEquals(new BigDecimal("12100.00"), inv.getTotal());
        assertEquals(deal.getId(), inv.getDealId());
        assertNotNull(inv.getFileSha256());
        assertNotNull(inv.getAccountCheck(), "the bank account was checked");
        assertEquals(ImportStatus.CONFIRMED, imports.get(up.document().getId()).getStatus());
        assertThrows(IllegalStateException.class, () -> imports.confirm(up.document().getId(), review(d, deal.getId(), null)));
        assertTrue(audit.history("ImportDocument", up.document().getId().toString()).stream()
                .anyMatch(r -> r.action().equals("READ") && r.actor().equals("claude")));

        // the same invoice again from another scan is refused
        ImportService.Upload again = imports.upload("faktura-sken.pdf", pdf(invoice(number, "Omítky Kolín import s.r.o.",
                "27082440").replace("Kolín\",\"country", "Kolín 2\",\"country")));
        for (int i = 0; i < 20 && imports.get(again.document().getId()).getStatus() == ImportStatus.QUEUED; i++) {
            imports.processNext();
        }
        ImportService.Detail d2 = imports.detail(again.document().getId());
        assertTrue(d2.suggestion().duplicateInvoice());
        assertEquals(out.counterpartyId(), d2.suggestion().match().id());
        assertThrows(ConflictException.class, () -> imports.confirm(again.document().getId(),
                review(d2, deal.getId(), out.counterpartyId())));
        imports.reject(again.document().getId(), "duplicate");
        assertEquals(ImportStatus.REJECTED, imports.get(again.document().getId()).getStatus());

        // the received invoice is in the deal's evidence package, with its file
        var overview = evidence.overview(deal.getId());
        assertTrue(overview.items().stream().anyMatch(i -> i.section() == EvidenceSection.INVOICES
                && i.title().startsWith(number) && !i.files().isEmpty() && !i.files().get(0).generated()));
    }

    @Test
    void contractGoesToTheDealAndFailuresAreRetriedThenReported() {
        Deal deal = wonDeal("Hala import smlouva");
        String contract = """
                {"documentType":"CONTRACT","summary":"Contract for works","documentNumber":"SOD-12","issueDate":"2026-09-01",
                "taxableDate":"","dueDate":"","currency":"","variableSymbol":"","issuer":{"name":"Aleruan-industry s.r.o.",
                "companyId":"14220997","vatNumber":"CZ14220997","address":"","country":"CZ","bankAccount":""},
                "recipient":{"name":"Investor Kolín a.s.","companyId":"","vatNumber":"","address":"","country":"CZ",
                "bankAccount":""},"netAmount":"","vatAmount":"","totalAmount":"","vatNote":"","references":"",
                "lines":[],"warnings":[]}""".replace("\n", "");
        ImportDocument doc = imports.upload("smlouva.pdf", pdf(contract)).document();
        for (int i = 0; i < 20 && imports.get(doc.getId()).getStatus() == ImportStatus.QUEUED; i++) {
            imports.processNext();
        }
        ImportService.Detail d = imports.detail(doc.getId());
        assertEquals("CUSTOMER", d.suggestion().role());
        assertEquals("Investor Kolín a.s.", d.suggestion().party().name(), "the other side, not Aleruan");
        ImportService.Outcome out = imports.confirm(doc.getId(), new ImportService.Review(DocType.CONTRACT, "SOD-12",
                null, null, null, null, null, null, null, null, null,
                new ImportService.PartyInput("Investor Kolín a.s.", "CZ", null, null, null, null), null, null,
                deal.getId()));
        assertEquals("DEAL_DOCUMENT", out.resultType());
        assertTrue(documents.list(deal.getId()).stream().anyMatch(f -> f.id().equals(out.resultId())));

        // a document that cannot be read goes back to the queue, and fails for good after three tries
        ImportDocument bad = imports.upload("FAIL.pdf", "%PDF-1.4\n% FAIL\n%%EOF\n".getBytes(StandardCharsets.UTF_8)).document();
        for (int i = 0; i < 30 && imports.get(bad.getId()).getStatus() != ImportStatus.FAILED; i++) {
            imports.processNext();
        }
        ImportDocument failed = imports.get(bad.getId());
        assertEquals(ImportStatus.FAILED, failed.getStatus());
        assertEquals(3, failed.getAttempts());
        assertTrue(failed.getError().contains("busy"));
        assertEquals(ImportStatus.QUEUED, imports.retry(bad.getId()).getStatus());
        imports.reject(bad.getId(), null);
    }
}
