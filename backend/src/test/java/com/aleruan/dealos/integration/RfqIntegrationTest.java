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
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.aleruan.dealos.subcontracting.application.RfqService;
import com.aleruan.dealos.subcontracting.domain.InviteTokens;
import com.aleruan.dealos.subcontracting.domain.Rfq;
import com.aleruan.dealos.subcontracting.domain.RfqItem;
import com.aleruan.dealos.subcontracting.domain.RfqStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RfqIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired RfqService rfqs;
    @Autowired AuditService audit;

    private Counterparty party(String name, String nip, CounterpartyRole role) {
        return counterparties.create(new CounterpartyService.NewCounterparty(name, "PL", nip, null, null, null, null,
                Set.of(role), Set.of("CONSTRUCTION")));
    }

    @Test
    void tenderFromInvitationToAwardUpdatesDealCosts() {
        Counterparty customer = party("Klient Testowy Sp. z o.o.", "2815901396", CounterpartyRole.CUSTOMER);
        Counterparty cheap = party("Tani Wykonawca Sp. z o.o.", "6330434839", CounterpartyRole.SUBCONTRACTOR);
        Counterparty pricey = party("Drogi Wykonawca Sp. z o.o.", "6828288072", CounterpartyRole.SUBCONTRACTOR);

        Deal deal = deals.create(new DealService.NewDeal("Hala, Kraków", customer.getId(), Direction.CONSTRUCTION,
                "PL", "EUR", new BigDecimal("25")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Tynkowanie", "CONSTRUCTION", "m2",
                new BigDecimal("100"), new BigDecimal("0"), null, new BigDecimal("60"), null));
        deal = deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Malowanie", "FINISHING", "m2",
                new BigDecimal("100"), new BigDecimal("0"), null, new BigDecimal("20"), null));
        Set<UUID> lineIds = deal.getLines().stream().map(l -> l.getId()).collect(Collectors.toSet());

        Rfq rfq = rfqs.create(deal.getId(), null, LocalDate.now().plusDays(7), lineIds);
        // the default title names the deal, never the customer's project name
        assertEquals("Poptávka " + deal.getNumber(), rfq.getTitle());
        assertEquals(2, rfq.getItems().size());

        String cheapToken = rfqs.invite(rfq.getId(), cheap.getId(), false).token();
        String priceyToken = rfqs.invite(rfq.getId(), pricey.getId(), false).token();
        assertThrows(IllegalStateException.class, () -> rfqs.invite(rfq.getId(), cheap.getId(), false)); // once only
        assertThrows(IllegalArgumentException.class, () -> rfqs.invite(rfq.getId(), customer.getId(), false));

        assertNotNull(rfqs.openByToken(cheapToken).getViewedAt());
        Map<String, UUID> itemIds = rfqs.get(rfq.getId()).getItems().stream()
                .collect(Collectors.toMap(RfqItem::getDescription, RfqItem::getId));
        // must price every item
        assertThrows(IllegalArgumentException.class, () -> rfqs.submitOffer(cheapToken,
                Map.of(itemIds.get("Tynkowanie"), new BigDecimal("40")), null));
        rfqs.submitOffer(cheapToken, Map.of(itemIds.get("Tynkowanie"), new BigDecimal("40"),
                itemIds.get("Malowanie"), new BigDecimal("12")), "Termin 3 tygodnie");
        rfqs.submitOffer(priceyToken, Map.of(itemIds.get("Tynkowanie"), new BigDecimal("50"),
                itemIds.get("Malowanie"), new BigDecimal("15")), null);

        Rfq withOffers = rfqs.get(rfq.getId());
        UUID cheapInvitation = withOffers.getInvitations().stream()
                .filter(i -> i.getSubcontractorId().equals(cheap.getId())).findFirst().orElseThrow().getId();
        assertEquals(cheapInvitation, withOffers.compare().cheapestCompleteOffer());

        rfqs.award(rfq.getId(), cheapInvitation);
        assertEquals(RfqStatus.AWARDED, rfqs.get(rfq.getId()).getStatus());
        Deal after = deals.get(deal.getId());
        assertEquals(new BigDecimal("8000.00"), after.totals().price());   // fixed selling prices unchanged
        assertEquals(new BigDecimal("5200.00"), after.totals().cost());    // 100×40 + 100×12
        assertTrue(after.getLines().stream().allMatch(l -> cheap.getId().equals(l.getSubcontractorId())));

        assertThrows(IllegalStateException.class, () -> rfqs.submitOffer(priceyToken,
                Map.of(itemIds.get("Tynkowanie"), BigDecimal.ONE, itemIds.get("Malowanie"), BigDecimal.ONE), null));
        assertThrows(NotFoundException.class, () -> rfqs.openByToken(InviteTokens.newToken()));
        assertTrue(audit.history(RfqService.AUDIT_TYPE, rfq.getId().toString()).stream()
                .anyMatch(r -> r.action().equals("OFFER_SUBMITTED") && r.actor().equals("subcontractor:" + cheap.getId())));
        assertTrue(audit.verifyChain().valid());
    }
}
