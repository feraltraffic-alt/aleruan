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
import com.aleruan.dealos.quote.application.QuoteService;
import java.math.BigDecimal;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChangeOrderIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired ChangeOrderService changeOrders;
    @Autowired QuoteService quotes;

    @Test
    void approvedChangeOrdersAddToTheContractAndPrintAsASheet() throws Exception {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Rodinný dům Říčany",
                "CZ", null, null, "Říčany", null, null, Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Deal deal = deals.create(new DealService.NewDeal("Omítky RD Říčany", customer.getId(), Direction.CONSTRUCTION,
                "CZ", "CZK", new BigDecimal("25")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Omítka Q2", "FINISHING", "m2",
                new BigDecimal("100"), new BigDecimal("400"), null, null, null));

        assertThrows(IllegalStateException.class, () -> changeOrders.create(deal.getId(),
                new ChangeOrderService.Draft("Q3", null, 0))); // the customer has not accepted yet
        deals.moveTo(deal.getId(), DealStatus.QUOTED);
        deals.moveTo(deal.getId(), DealStatus.WON);

        ChangeOrder co = changeOrders.create(deal.getId(), new ChangeOrderService.Draft("Omítka Q3 místo Q2",
                "Zákazník chce hladší povrch", 3));
        assertEquals(1, co.getSeq());
        changeOrders.addLine(co.getId(), new DealService.LineData(LineKind.WORK, "Příplatek za kvalitu Q3", "FINISHING",
                "m2", new BigDecimal("100"), new BigDecimal("80"), null, null, null));
        changeOrders.send(co.getId());

        ChangeOrderService.Contract pending = changeOrders.contract(deals.get(deal.getId()));
        assertEquals(new BigDecimal("50000.00"), pending.total().price());
        assertEquals(new BigDecimal("10000.00"), pending.pendingPrice());

        byte[] pdf = quotes.changeOrderPdf(co.getId(), null);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc).replace(' ', ' ');
            assertTrue(text.contains("Změnový list"), text);
            assertTrue(text.contains(deals.get(deal.getId()).getNumber() + "-C1"));
            assertTrue(text.contains("Zákazník chce hladší povrch"));
            assertTrue(text.contains("Odsouhlasení objednatelem"));
        }

        changeOrders.approve(co.getId(), new ChangeOrderService.Decision("Ing. Petra Malá", null));
        ChangeOrderService.Contract agreed = changeOrders.contract(deals.get(deal.getId()));
        assertEquals(new BigDecimal("60000.00"), agreed.total().price());
        assertEquals(new BigDecimal("10000.00"), agreed.approvedChanges().price());
        assertEquals(3, agreed.extraDays());
        assertEquals(new BigDecimal("60000.00"), deals.contractValue(deal.getId()));

        ChangeOrder second = changeOrders.create(deal.getId(), new ChangeOrderService.Draft("Úklid", null, null));
        assertEquals(2, second.getSeq());
        changeOrders.addLine(second.getId(), new DealService.LineData(LineKind.SERVICE, "Úklid po stavbě", null, "kpl",
                BigDecimal.ONE, new BigDecimal("3000"), null, null, null));
        deals.moveTo(deal.getId(), DealStatus.IN_PROGRESS);
        deals.moveTo(deal.getId(), DealStatus.COMPLETED);
        IllegalStateException closed = assertThrows(IllegalStateException.class, () -> changeOrders.send(second.getId()));
        assertTrue(closed.getMessage().contains("COMPLETED"), closed.getMessage());
    }
}
