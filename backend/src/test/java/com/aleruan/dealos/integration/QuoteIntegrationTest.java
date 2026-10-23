package com.aleruan.dealos.integration;

import com.aleruan.dealos.platform.documents.DocLanguage;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.quote.application.QuoteService;
import com.aleruan.dealos.quote.domain.Quote;
import com.aleruan.dealos.quote.domain.VatAdvisor;
import com.aleruan.dealos.quote.domain.VatMode;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class QuoteIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired QuoteService quotes;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuesVersionedQuotesWithPdfAndFreezesAfterAcceptance() throws Exception {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Wisła Budownictwo Sp. z o.o.",
                "PL", "9898347887", "PL9898347887", "ul. Długa 5, 31-147 Kraków", null, null,
                Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Deal deal = deals.create(new DealService.NewDeal("Rekonstrukce kanceláří, Kraków", customer.getId(),
                Direction.CONSTRUCTION, "PL", "EUR", new BigDecimal("25")));

        assertThrows(IllegalStateException.class,
                () -> quotes.issue(deal.getId(), new QuoteService.IssueRequest(null, null, null, null, null)));

        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Sádrokartonové příčky", "INTERIOR", "m2",
                new BigDecimal("180"), new BigDecimal("24"), new BigDecimal("30"), null, null));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Elektroinstalace", "ELECTRICAL", "kpl",
                BigDecimal.ONE, new BigDecimal("6800"), null, new BigDecimal("8200"), null));

        QuoteService.Defaults defaults = quotes.defaults(deal.getId());
        assertEquals(DocLanguage.PL, defaults.language());
        assertEquals(VatMode.REVERSE_CHARGE_EU, defaults.vatMode());
        assertEquals(VatAdvisor.Reason.SITE_IN_OTHER_EU_STATE, defaults.reason());

        Quote v1 = quotes.issue(deal.getId(), new QuoteService.IssueRequest(DocLanguage.CS, 14, null, null,
                "Záloha 30 % před zahájením"));
        assertEquals(1, v1.getVersion());
        assertTrue(v1.getNumber().matches("Q-\\d{4}-\\d{4}"));
        assertEquals(new BigDecimal("13816.00"), v1.getTotal()); // 180 × 31.20 + 8200, no Czech VAT
        assertEquals(v1.getIssuedOn().plusDays(14), v1.getValidUntil());
        assertEquals(DealStatus.QUOTED, deals.get(deal.getId()).getStatus());

        byte[] pdf = v1.getPdf();
        assertEquals("%PDF", new String(pdf, 0, 4));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)), v1.getPdfSha256());
        String text;
        try (PDDocument doc = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(doc).replace(' ', ' ');
        }
        assertTrue(text.contains("Cenová nabídka"), text);
        assertTrue(text.contains("Wisła Budownictwo"), "Polish letters survive the font subset");
        assertTrue(text.contains("Sádrokartonové příčky"), "Czech letters survive the font subset");
        assertTrue(text.contains("13 816,00 EUR"), text);
        assertTrue(text.contains("194"), "reverse charge note");

        // a second version with Czech VAT chosen by hand
        Quote v2 = quotes.issue(deal.getId(), new QuoteService.IssueRequest(DocLanguage.EN, null, VatMode.STANDARD,
                null, null));
        assertEquals(2, v2.getVersion());
        assertEquals(v1.getNumber() + "/2", v2.displayNumber());
        assertEquals(new BigDecimal("2901.36"), v2.getVatAmount());
        assertEquals(new BigDecimal("16717.36"), v2.getTotal());
        assertEquals(2, quotes.list(deal.getId()).size());
        assertTrue(quotes.html(v2.getId()).contains("Commercial offer"));

        // issued quotes are evidence: the database refuses changes
        assertThrows(Exception.class, () -> jdbc.update("update quote set total = 1 where id = ?", v1.getId()));
        assertThrows(Exception.class, () -> jdbc.update("delete from quote where id = ?", v1.getId()));

        deals.moveTo(deal.getId(), DealStatus.WON);
        assertThrows(IllegalStateException.class,
                () -> quotes.issue(deal.getId(), new QuoteService.IssueRequest(null, null, null, null, null)));
        assertEquals(2, audit.history(QuoteService.AUDIT_TYPE, v2.getId().toString()).size()
                + audit.history(QuoteService.AUDIT_TYPE, v1.getId().toString()).size());
        assertTrue(audit.verifyChain().valid());
    }

    @Test
    void czechConstructionForVatPayerSuggestsDomesticReverseCharge() {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Stavitel Test s.r.o.",
                "CZ", "21590109", "CZ21590109", "Vinohradská 1, Praha", null, null,
                Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Deal deal = deals.create(new DealService.NewDeal("Byt Vinohrady", customer.getId(), Direction.CONSTRUCTION,
                null, "CZK", new BigDecimal("20")));
        QuoteService.Defaults d = quotes.defaults(deal.getId());
        assertEquals(VatMode.REVERSE_CHARGE_CZ, d.vatMode());
        assertEquals(DocLanguage.CS, d.language());
    }
}
