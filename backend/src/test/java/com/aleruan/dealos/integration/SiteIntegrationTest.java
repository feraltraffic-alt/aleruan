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
import com.aleruan.dealos.platform.web.ConflictException;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SiteIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired SiteService sites;

    @Test
    void siteOfAWonDealWithScheduleFiltersAndWorkload() {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Montaż Wesel GmbH", "DE",
                null, "DE129273398", "Industriestraße 4, Wesel", null, null, Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Counterparty crew = counterparties.create(new CounterpartyService.NewCounterparty("Fasády Brno s.r.o.", "CZ",
                null, null, null, null, null, Set.of(CounterpartyRole.SUBCONTRACTOR), Set.of("FINISHING")));
        Deal deal = deals.create(new DealService.NewDeal("Fasáda, Wesel", customer.getId(), Direction.CONSTRUCTION, "DE",
                "EUR", new BigDecimal("20")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Zateplení fasády", "FINISHING", "m2",
                new BigDecimal("400"), new BigDecimal("35"), null, null, crew.getId()));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.MATERIAL, "Polystyren", null, "m2",
                new BigDecimal("400"), new BigDecimal("9"), null, null, null));

        assertThrows(IllegalStateException.class, () -> sites.create(deal.getId())); // not won yet
        deals.moveTo(deal.getId(), DealStatus.QUOTED);
        deals.moveTo(deal.getId(), DealStatus.WON);
        Site site = sites.create(deal.getId());
        assertEquals("Fasáda, Wesel", site.getName());
        assertEquals("DE", site.getCountry());
        assertThrows(ConflictException.class, () -> sites.create(deal.getId()));

        assertThrows(IllegalStateException.class, () -> sites.tasksFromDeal(site.getId())); // no dates yet
        LocalDate start = LocalDate.of(2026, 11, 2);
        sites.edit(site.getId(), new Site.Details("Fasáda, Wesel", "Industriestraße 4", "Wesel", "DE",
                new BigDecimal("51.658600"), new BigDecimal("6.617800"), "lena", "pavel", start, start.plusDays(25),
                SiteStatus.ACTIVE, null));
        Site withTasks = sites.tasksFromDeal(site.getId());
        assertEquals(1, withTasks.getTasks().size(), "only work lines become tasks");
        assertEquals(crew.getId(), withTasks.getTasks().get(0).getSubcontractorId());
        assertEquals(1, sites.tasksFromDeal(site.getId()).getTasks().size(), "running it twice adds nothing");

        sites.addTask(site.getId(), new SiteService.TaskData("Lešení", "INSTALLATION", crew.getId(), start, start.plusDays(3), 50));
        assertThrows(IllegalArgumentException.class, () -> sites.addTask(site.getId(),
                new SiteService.TaskData("x", null, customer.getId(), start, start, 0))); // a customer cannot do the work

        assertEquals(List.of(site.getId()), sites.search(new SiteService.Filter(null, "de", "wesel", null, customer.getId(),
                "FINISHING", null)).stream().map(Site::getId).toList());
        assertTrue(sites.search(new SiteService.Filter(SiteStatus.DONE, null, null, null, null, null, null)).stream()
                .noneMatch(s -> s.getId().equals(site.getId())));
        assertTrue(sites.search(new SiteService.Filter(null, null, null, "LENA", null, null, null)).stream()
                .anyMatch(s -> s.getId().equals(site.getId())));
        assertTrue(sites.forDeal(deal.getId()).isPresent());

        List<SiteService.WorkloadRow> load = sites.workload(start, start.plusDays(13));
        SiteService.WorkloadRow row = load.stream().filter(r -> r.subcontractor().id().equals(crew.getId())).findFirst()
                .orElseThrow();
        assertEquals(2, row.tasks().size());
        assertEquals(List.of(2, 1), row.weeks().stream().map(w -> w.parallelTasks()).toList());
    }
}
