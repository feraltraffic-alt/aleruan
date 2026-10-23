package com.aleruan.dealos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.invoice.application.InvoiceService;
import com.aleruan.dealos.invoice.domain.Invoice;
import com.aleruan.dealos.invoice.domain.InvoiceKind;
import com.aleruan.dealos.invoice.domain.InvoiceStatus;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.VatMode;
import com.aleruan.dealos.site.application.AcceptanceService;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.StageStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class InvoiceIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired SiteService sites;
    @Autowired AcceptanceService acceptance;
    @Autowired InvoiceService invoices;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;

    @Test
    void stageInvoiceCzkVatNumberingPaymentAndCreditNote() throws Exception {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Bauherr Emmerich GmbH", "DE",
                null, "DE811569869", "Kasernenstraße 1, Emmerich", null, null, Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Deal deal = deals.create(new DealService.NewDeal("Lager Emmerich", customer.getId(), Direction.CONSTRUCTION, "DE",
                "EUR", new BigDecimal("25")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Montáž skladu", "INSTALLATION", "kpl",
                BigDecimal.ONE, new BigDecimal("32000"), null, null, null));
        assertThrows(IllegalStateException.class, () -> invoices.createDraft(deal.getId(), InvoiceService.Source.EMPTY, null));
        deals.moveTo(deal.getId(), DealStatus.QUOTED);
        deals.moveTo(deal.getId(), DealStatus.WON);

        Site site = sites.create(deal.getId());
        AcceptanceStage stage = acceptance.create(site.getId(), new AcceptanceService.StageData("Ocelová konstrukce", null,
                new BigDecimal("24000"), null, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> invoices.createDraft(deal.getId(), InvoiceService.Source.STAGE, stage.getId())); // not accepted yet
        acceptance.submit(stage.getId());
        acceptance.decide(stage.getId(), new AcceptanceService.Decision(StageStatus.ACCEPTED, "Herr Brandt", null, null));

        // stage invoice: German customer, site in Germany → EU reverse charge, German labels next to Czech
        Invoice draft = invoices.createDraft(deal.getId(), InvoiceService.Source.STAGE, stage.getId());
        assertEquals(VatMode.REVERSE_CHARGE_EU, draft.getVatMode());
        assertEquals(DocLanguage.DE, draft.getLanguage());
        assertEquals(new BigDecimal("24000.00"), draft.getTotal());
        assertThrows(IllegalStateException.class, () -> invoices.createDraft(deal.getId(), InvoiceService.Source.STAGE, stage.getId()));

        Invoice issued = invoices.issue(draft.getId(), null);
        int year = invoices.today().getYear();
        assertTrue(issued.getNumber().matches("FV-" + year + "-\\d{4}"));
        assertEquals(issued.getNumber().replaceAll("\\D", ""), issued.getVariableSymbol());
        assertEquals(new BigDecimal("25.000"), issued.getExchangeRate().setScale(3));
        assertEquals(0, issued.getVatAmountCzk().signum());
        String text = text(issued.getPdf());
        assertTrue(text.contains("Faktura – daňový doklad / Rechnung"), text);
        assertTrue(text.contains("Etapa 1: Ocelová konstrukce"));
        assertTrue(text.contains("194"), "reverse charge note for a site abroad");
        assertTrue(text.contains("24.000,00 EUR"), text);
        assertThrows(IllegalStateException.class, () -> invoices.edit(issued.getId(), settings(VatMode.STANDARD), List.of()));
        assertThrows(Exception.class, () -> jdbc.update("update invoice set total = 1 where id = ?", issued.getId()));
        assertThrows(Exception.class, () -> jdbc.update("delete from invoice where id = ?", issued.getId()));

        // a second invoice with Czech VAT: VAT also in CZK at the rate of the date of supply
        Invoice second = invoices.createDraft(deal.getId(), InvoiceService.Source.EMPTY, null);
        invoices.edit(second.getId(), settings(VatMode.STANDARD), List.of(
                new Invoice.LineData("Záloha na opláštění", null, BigDecimal.ONE, new BigDecimal("1000"), null)));
        second = invoices.issue(second.getId(), null);
        assertEquals(new BigDecimal("210.00"), second.getVatAmount());
        assertEquals(new BigDecimal("5250.00"), second.getVatAmountCzk());
        assertEquals(Integer.parseInt(issued.getNumber().substring(8)) + 1, Integer.parseInt(second.getNumber().substring(8)),
                "numbers follow without gaps");
        String secondText = text(second.getPdf());
        assertTrue(secondText.contains("5 250,00 CZK") || secondText.contains("5 250,00 CZK"), secondText);

        Invoice paidPart = invoices.pay(issued.getId(), new BigDecimal("4000"), invoices.today());
        assertEquals(InvoiceStatus.ISSUED, paidPart.getStatus());
        assertEquals(InvoiceStatus.PAID, invoices.pay(issued.getId(), new BigDecimal("20000"), invoices.today()).getStatus());

        InvoiceService.DealInvoicing pos = invoices.dealInvoicing(deal.getId());
        assertEquals(new BigDecimal("40000.00"), pos.contractValue().setScale(2));
        assertEquals(new BigDecimal("25000.00"), pos.invoiced().setScale(2));
        assertEquals(new BigDecimal("24000.00"), pos.paid().setScale(2));
        assertTrue(pos.stages().get(0).invoiced());

        Invoice credit = invoices.credit(second.getId(), "Chybná částka zálohy");
        assertEquals(InvoiceKind.CREDIT_NOTE, credit.getKind());
        assertTrue(credit.getNumber().startsWith("OD-" + year));
        assertEquals(new BigDecimal("-1210.00"), credit.getTotal());
        assertEquals(new BigDecimal("-5250.00"), credit.getVatAmountCzk(), "the credit note uses the original rate");
        assertEquals(InvoiceStatus.CREDITED, invoices.get(second.getId()).getStatus());
        assertTrue(text(credit.getPdf()).contains("Opravný daňový doklad"));
        assertEquals(new BigDecimal("24000.00"), invoices.dealInvoicing(deal.getId()).invoiced().setScale(2));
        assertNotNull(invoices.html(credit.getId()));
        assertTrue(audit.verifyChain().valid());
    }

    private Invoice.Settings settings(VatMode mode) {
        return new Invoice.Settings(DocLanguage.CS, mode, new BigDecimal("21"), invoices.today(), invoices.today().plusDays(14), null);
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace(' ', ' ');
        }
    }
}
