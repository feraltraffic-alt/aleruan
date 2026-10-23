package com.aleruan.dealos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.deal.application.ChangeOrderService;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.ChangeOrder;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.site.application.AcceptanceService;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteStatus;
import com.aleruan.dealos.site.domain.StageStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AcceptanceIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired ChangeOrderService changeOrders;
    @Autowired SiteService sites;
    @Autowired AcceptanceService acceptance;

    @Test
    void stagesCoverTheContractAndPrintProtocols() throws Exception {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Bauherr Kleve GmbH", "DE",
                null, "DE136695976", "Hafenstraße 2, Kleve", null, null, Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Deal deal = deals.create(new DealService.NewDeal("Hala Kleve", customer.getId(), Direction.CONSTRUCTION, "DE",
                "EUR", new BigDecimal("20")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Montáž haly", "INSTALLATION", "kpl",
                BigDecimal.ONE, new BigDecimal("50000"), null, null, null));
        deals.moveTo(deal.getId(), DealStatus.QUOTED);
        deals.moveTo(deal.getId(), DealStatus.WON);
        ChangeOrder co = changeOrders.create(deal.getId(), new ChangeOrderService.Draft("Vrata navíc", null, 0));
        changeOrders.addLine(co.getId(), new DealService.LineData(LineKind.WORK, "Sekční vrata", null, "ks",
                BigDecimal.ONE, new BigDecimal("5000"), null, null, null));
        changeOrders.send(co.getId());
        changeOrders.approve(co.getId(), new ChangeOrderService.Decision("Herr Lang", null));

        Site site = sites.create(deal.getId());
        LocalDate start = LocalDate.of(2027, 4, 5);
        sites.edit(site.getId(), new Site.Details("Hala Kleve", "Hafenstraße 2", "Kleve", "DE", null, null, "lena", "pavel",
                start, start.plusDays(40), SiteStatus.ACTIVE, null));
        UUID task = sites.tasksFromDeal(site.getId()).getTasks().get(0).getId();

        AcceptanceStage first = acceptance.create(site.getId(), new AcceptanceService.StageData("Ocelová konstrukce",
                "Sloupy a vazníky", new BigDecimal("36000"), start.plusDays(20), Set.of(task)));
        acceptance.create(site.getId(), new AcceptanceService.StageData("Opláštění", null, new BigDecimal("30000"), null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> acceptance.create(site.getId(),
                new AcceptanceService.StageData("x", null, BigDecimal.ONE, null, Set.of(UUID.randomUUID()))));

        AcceptanceService.Coverage coverage = acceptance.coverage(site.getId());
        assertEquals(new BigDecimal("66000.00"), coverage.contractValue()); // 60 000 + approved 6 000
        assertEquals(new BigDecimal("66000.00"), coverage.planned());
        assertEquals(0, coverage.unassigned().signum());

        acceptance.submit(first.getId());
        String blank = text(acceptance.protocol(first.getId(), null));
        assertTrue(blank.contains("Abnahmeprotokoll"), "German customer gets German by default");
        assertTrue(blank.contains("Ocelová konstrukce"));
        assertTrue(blank.contains("Montáž haly"), "linked tasks are listed as work performed");
        assertTrue(blank.contains("36.000,00 EUR"), blank);

        acceptance.decide(first.getId(), new AcceptanceService.Decision(StageStatus.ACCEPTED_WITH_REMARKS, "Herr Lang",
                "Kotvení sloupu A3 dotáhnout", start.plusDays(30)));
        String done = text(acceptance.protocol(first.getId(), com.aleruan.dealos.platform.documents.DocLanguage.CS));
        assertTrue(done.contains("Předávací protokol"));
        assertTrue(done.contains("Kotvení sloupu A3 dotáhnout"));
        assertTrue(done.contains("Herr Lang"));
        assertEquals(new BigDecimal("36000.00"), acceptance.coverage(site.getId()).accepted());
        assertThrows(IllegalStateException.class, () -> acceptance.remove(first.getId()));
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace(' ', ' ');
        }
    }
}
