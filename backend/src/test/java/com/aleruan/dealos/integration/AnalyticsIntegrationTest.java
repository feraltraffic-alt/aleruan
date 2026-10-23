package com.aleruan.dealos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.analytics.application.AnalyticsService;
import com.aleruan.dealos.analytics.application.MarginCuts;
import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.deal.application.ChangeOrderService;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.Site;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AnalyticsIntegrationTest extends IntegrationTestBase {

    /** A country no other test uses, so the totals here are this test's own. */
    private static final String COUNTRY = "LU";

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired ChangeOrderService changeOrders;
    @Autowired SiteService sites;
    @Autowired AnalyticsService analytics;

    @Test
    void marginInOneCurrencySitesAndRisks() {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Luxbau S.A.", "LU",
                null, null, null, null, null, Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Counterparty crew = counterparties.create(new CounterpartyService.NewCounterparty("Montáže Lux s.r.o.", "CZ",
                null, null, null, null, null, Set.of(CounterpartyRole.SUBCONTRACTOR), Set.of()));

        Deal czk = deals.create(new DealService.NewDeal("Hala Esch", customer.getId(), Direction.CONSTRUCTION, COUNTRY,
                "CZK", new BigDecimal("20")));
        deals.addLine(czk.getId(), new DealService.LineData(LineKind.WORK, "Montáž", "INSTALLATION", "t",
                BigDecimal.TEN, new BigDecimal("1000"), null, null, crew.getId()));
        deals.moveTo(czk.getId(), DealStatus.QUOTED);
        deals.moveTo(czk.getId(), DealStatus.WON);

        Deal eur = deals.create(new DealService.NewDeal("Sklad Wiltz", customer.getId(), Direction.CONSTRUCTION, COUNTRY,
                "EUR", new BigDecimal("20")));
        deals.addLine(eur.getId(), new DealService.LineData(LineKind.MATERIAL, "Plech", null, "kpl",
                BigDecimal.TEN, new BigDecimal("100"), null, null, null));
        deals.moveTo(eur.getId(), DealStatus.QUOTED);
        deals.moveTo(eur.getId(), DealStatus.WON);

        Deal quoted = deals.create(new DealService.NewDeal("Nabídka Diekirch", customer.getId(), Direction.CONSTRUCTION,
                COUNTRY, "CZK", new BigDecimal("50")));
        deals.addLine(quoted.getId(), new DealService.LineData(LineKind.WORK, "Projekt", null, "kpl",
                BigDecimal.ONE, new BigDecimal("1000"), null, null, null));
        deals.moveTo(quoted.getId(), DealStatus.QUOTED);

        // agreed deals in CZK: 12 000 + 1 200 EUR × 25 = 42 000 price, 10 000 + 25 000 cost
        AnalyticsService.Margin m = analytics.margin(filter(AnalyticsService.Scope.CONTRACTED, "CZK"));
        assertEquals(2, m.totals().deals());
        assertEquals(new BigDecimal("42000.00"), m.totals().price());
        assertEquals(new BigDecimal("35000.00"), m.totals().cost());
        assertEquals(new BigDecimal("16.7"), m.totals().marginPct());
        assertTrue(m.rates().stream().anyMatch(r -> r.currency().equals("EUR")));
        assertTrue(m.missingRates().isEmpty());

        List<MarginCuts.Row> subs = m.cuts().get(MarginCuts.Cut.SUBCONTRACTOR);
        assertEquals("Montáže Lux s.r.o.", subs.stream().filter(r -> r.key().equals(crew.getId().toString()))
                .findFirst().orElseThrow().label());
        assertTrue(subs.stream().anyMatch(r -> r.key().equals(MarginCuts.NONE)));
        assertEquals(1, m.cuts().get(MarginCuts.Cut.CUSTOMER).size());
        assertEquals(2, m.cuts().get(MarginCuts.Cut.CUSTOMER).get(0).deals());

        // the same in EUR
        AnalyticsService.Margin inEur = analytics.margin(filter(AnalyticsService.Scope.CONTRACTED, "EUR"));
        assertEquals(new BigDecimal("1680.00"), inEur.totals().price());

        // open quotes are a separate view
        AnalyticsService.Margin pipeline = analytics.margin(filter(AnalyticsService.Scope.PIPELINE, "CZK"));
        assertEquals(1, pipeline.totals().deals());
        assertEquals(new BigDecimal("1500.00"), pipeline.totals().price());

        // a period that ends before the deals were made has nothing
        AnalyticsService.Margin before = analytics.margin(new AnalyticsService.Filter(AnalyticsService.Scope.CONTRACTED,
                null, LocalDate.of(2020, 1, 1), null, COUNTRY, "CZK"));
        assertEquals(0, before.totals().deals());
        assertThrows(IllegalArgumentException.class,
                () -> analytics.margin(filter(AnalyticsService.Scope.CONTRACTED, "XXX")));

        // health: a late task, a change order waiting for the customer, unchecked counterparties, package gaps
        Site site = sites.create(czk.getId());
        sites.addTask(site.getId(), new SiteService.TaskData("Montáž", "INSTALLATION", crew.getId(),
                sites.today().minusDays(10), sites.today().minusDays(2), 50));
        var co = changeOrders.create(czk.getId(), new ChangeOrderService.Draft("Navíc kotvy", null, 0));
        changeOrders.addLine(co.getId(), new DealService.LineData(LineKind.WORK, "Kotvy", null, "ks",
                BigDecimal.TEN, new BigDecimal("50"), null, null, null));
        changeOrders.send(co.getId());

        AnalyticsService.Health h = analytics.health();
        AnalyticsService.SiteRow row = h.sites().stream().filter(s -> s.siteId().equals(site.getId())).findFirst()
                .orElseThrow();
        assertEquals(1, row.lateTasks());
        assertEquals(1, row.pendingChangeOrders());
        assertEquals(new BigDecimal("600.00"), row.pendingChangePrice());
        assertEquals(czk.getNumber(), row.dealNumber());

        AnalyticsService.PartyRisk sub = h.counterparties().stream().filter(p -> p.id().equals(crew.getId()))
                .findFirst().orElseThrow();
        assertEquals(null, sub.status(), "never checked");
        assertEquals(Set.of("SUBCONTRACTOR"), sub.roles());
        assertTrue(sub.deals().contains(czk.getNumber()));
        AnalyticsService.PartyRisk cust = h.counterparties().stream().filter(p -> p.id().equals(customer.getId()))
                .findFirst().orElseThrow();
        assertEquals(2, cust.deals().size());

        AnalyticsService.PackageRisk pkg = h.packages().stream().filter(p -> p.dealId().equals(czk.getId()))
                .findFirst().orElseThrow();
        assertTrue(pkg.missing() > 0);
        assertTrue(pkg.gaps().stream().anyMatch(g -> g.code().equals("CONTRACT_ATTACHED")));
        assertTrue(pkg.gaps().stream().noneMatch(g -> g.code().equals("AUDIT_CHAIN")), "chain is not checked per deal");
    }

    private static AnalyticsService.Filter filter(AnalyticsService.Scope scope, String currency) {
        return new AnalyticsService.Filter(scope, null, null, null, COUNTRY, currency);
    }
}
