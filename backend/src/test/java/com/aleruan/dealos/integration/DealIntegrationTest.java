package com.aleruan.dealos.integration;

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
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DealIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired AuditService audit;

    private Counterparty party(String name, String ico, CounterpartyRole role) {
        return counterparties.create(new CounterpartyService.NewCounterparty(name, "CZ", ico, null, null, null, null,
                Set.of(role), Set.of("CONSTRUCTION")));
    }

    @Test
    void dealLifecycleWithMarginAndFrozenPrices() {
        Counterparty customer = party("Zákazník Demo s.r.o.", "68599528", CounterpartyRole.CUSTOMER);
        Counterparty sub = party("Stavby Demo s.r.o.", "17603137", CounterpartyRole.SUBCONTRACTOR);

        Deal deal = deals.create(new DealService.NewDeal("Omítky, Drebber", customer.getId(), Direction.CONSTRUCTION,
                "de", "EUR", new BigDecimal("30")));
        assertTrue(deal.getNumber().matches("D-\\d{4}-\\d{4}"));
        assertEquals("DE", deal.getSiteCountry());

        deal = deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Odstranění a nová omítka",
                "CONSTRUCTION", "m2", new BigDecimal("100"), new BigDecimal("50"), null, null, sub.getId()));
        UUID lineId = deal.getLines().get(0).getId();
        assertEquals(new BigDecimal("6500.00"), deal.totals().price());
        assertEquals(new BigDecimal("1500.00"), deal.totals().margin());

        // the customer may only be a customer, the line supplier only a subcontractor/supplier/carrier
        assertThrows(IllegalArgumentException.class, () -> deals.create(new DealService.NewDeal("x", sub.getId(),
                Direction.CONSTRUCTION, null, "EUR", BigDecimal.TEN)));

        deals.moveTo(deal.getId(), DealStatus.QUOTED);
        deals.moveTo(deal.getId(), DealStatus.WON);
        UUID dealId = deal.getId();
        assertThrows(IllegalStateException.class, () -> deals.updateLine(dealId, lineId, new DealService.LineData(
                LineKind.WORK, "Odstranění a nová omítka", "CONSTRUCTION", "m2", new BigDecimal("120"),
                new BigDecimal("50"), null, null, sub.getId())));

        // tender result after winning: cost changes, selling price stays
        deals.assignCost(dealId, lineId, new BigDecimal("45"), sub.getId(), "RFQ test");
        Deal after = deals.get(dealId);
        assertEquals(new BigDecimal("6500.00"), after.totals().price());
        assertEquals(new BigDecimal("2000.00"), after.totals().margin());

        assertThrows(IllegalStateException.class, () -> deals.moveTo(dealId, DealStatus.DRAFT));
        assertEquals(java.util.List.of("CREATED", "LINE_ADDED", "STATUS_CHANGED", "STATUS_CHANGED", "COST_ASSIGNED"),
                audit.history(DealService.AUDIT_TYPE, dealId.toString()).stream().map(r -> r.action()).toList());
        assertTrue(audit.verifyChain().valid());
    }
}
