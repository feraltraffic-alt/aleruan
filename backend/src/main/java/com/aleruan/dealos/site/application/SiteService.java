package com.aleruan.dealos.site.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.DealSummary;
import com.aleruan.dealos.deal.DealCatalog.LineSummary;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.ConflictException;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteRepository;
import com.aleruan.dealos.site.domain.SiteStatus;
import com.aleruan.dealos.site.domain.SiteTask;
import com.aleruan.dealos.site.domain.Workload;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteService {

    public static final String AUDIT_TYPE = "Site";
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final Set<String> EXECUTABLE = Set.of("WON", "IN_PROGRESS");

    public record Filter(SiteStatus status, String country, String city, String projectManager, UUID customerId,
                         String workType, UUID dealId) {
    }

    public record TaskData(String title, String workType, UUID subcontractorId, LocalDate start, LocalDate end,
                           Integer progressPct) {
    }

    /** A subcontractor's tasks across sites in a period, with its weekly peak load. */
    public record WorkloadRow(CounterpartySummary subcontractor, List<SiteTask> tasks, List<Workload.Week> weeks) {
    }

    private final SiteRepository sites;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;

    SiteService(SiteRepository sites, DealCatalog deals, CounterpartyLookup counterparties, AuditService audit,
                CurrentActor actor, Clock clock) {
        this.sites = sites;
        this.deals = deals;
        this.counterparties = counterparties;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(PRAGUE));
    }

    /** Opens the site of a won deal, named after the deal and placed in its site country. */
    @Transactional
    public Site create(UUID dealId) {
        DealSummary deal = deal(dealId);
        if (!EXECUTABLE.contains(deal.status())) {
            throw new IllegalStateException("A site is opened once the customer accepted deal " + deal.number());
        }
        if (sites.existsByDealId(dealId)) {
            throw new ConflictException("Deal " + deal.number() + " already has a site");
        }
        Site site = sites.save(new Site(dealId, deal.title(), deal.siteCountry(), clock.instant(), actor.name()));
        audit.record(AUDIT_TYPE, site.getId().toString(), "CREATED", Map.of("dealId", dealId, "name", site.getName()));
        return site;
    }

    @Transactional
    public Site edit(UUID id, Site.Details details) {
        Site site = get(id);
        site.edit(details);
        audit.record(AUDIT_TYPE, id.toString(), "EDITED", details);
        return site;
    }

    @Transactional
    public Site addTask(UUID siteId, TaskData t) {
        Site site = get(siteId);
        requireSubcontractor(t.subcontractorId());
        SiteTask task = site.addTask(t.title(), t.workType(), null, t.subcontractorId(), t.start(), t.end(), progress(t));
        audit.record(AUDIT_TYPE, siteId.toString(), "TASK_ADDED", Map.of("taskId", task.getId(), "task", t));
        return site;
    }

    @Transactional
    public Site updateTask(UUID siteId, UUID taskId, TaskData t) {
        Site site = get(siteId);
        requireSubcontractor(t.subcontractorId());
        site.updateTask(taskId, t.title(), t.workType(), t.subcontractorId(), t.start(), t.end(), progress(t));
        audit.record(AUDIT_TYPE, siteId.toString(), "TASK_UPDATED", Map.of("taskId", taskId, "task", t));
        return site;
    }

    @Transactional
    public Site removeTask(UUID siteId, UUID taskId) {
        Site site = get(siteId);
        site.removeTask(taskId);
        audit.record(AUDIT_TYPE, siteId.toString(), "TASK_REMOVED", Map.of("taskId", taskId));
        return site;
    }

    /**
     * One task per work line of the deal that is not on the schedule yet, spanning the planned dates,
     * with the line's work type and subcontractor.
     */
    @Transactional
    public Site tasksFromDeal(UUID siteId) {
        Site site = get(siteId);
        if (site.getPlannedStart() == null || site.getPlannedEnd() == null) {
            throw new IllegalStateException("Set the planned start and end of the site first");
        }
        Set<UUID> linked = site.getTasks().stream().map(SiteTask::getDealLineId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        int added = 0;
        for (LineSummary line : deals.lines(site.getDealId())) {
            if (!"WORK".equals(line.kind()) || linked.contains(line.id())) {
                continue;
            }
            site.addTask(line.description(), line.workType(), line.id(), line.subcontractorId(), site.getPlannedStart(),
                    site.getPlannedEnd(), 0);
            added++;
        }
        audit.record(AUDIT_TYPE, siteId.toString(), "TASKS_FROM_DEAL", Map.of("added", added));
        return site;
    }

    @Transactional(readOnly = true)
    public Site get(UUID id) {
        return sites.findById(id).orElseThrow(() -> new NotFoundException("Site " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Site> forDeal(UUID dealId) {
        return sites.findByDealId(dealId);
    }

    @Transactional(readOnly = true)
    public List<Site> search(Filter f) {
        List<Site> found = sites.search(f.status(), blank(f.country()) == null ? null : f.country().toUpperCase(),
                blank(f.city()), blank(f.projectManager()), f.dealId());
        if (f.customerId() == null && blank(f.workType()) == null) {
            return found;
        }
        Map<UUID, DealSummary> dealCache = new HashMap<>();
        return found.stream()
                .filter(s -> f.customerId() == null || deal(s.getDealId(), dealCache)
                        .map(d -> f.customerId().equals(d.customerId())).orElse(false))
                .filter(s -> blank(f.workType()) == null || workTypes(s).contains(f.workType()))
                .toList();
    }

    /** Work types on the site: from its tasks and from the deal lines. */
    @Transactional(readOnly = true)
    public Set<String> workTypes(Site site) {
        Set<String> types = site.getTasks().stream().map(SiteTask::getWorkType).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        deals.lines(site.getDealId()).stream().map(LineSummary::workType).filter(java.util.Objects::nonNull)
                .forEach(types::add);
        return types;
    }

    @Transactional(readOnly = true)
    public List<WorkloadRow> workload(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) {
            throw new IllegalArgumentException("Choose a period of up to about a year");
        }
        Map<UUID, List<SiteTask>> bySub = new LinkedHashMap<>();
        for (SiteTask t : sites.tasksBetween(from, to)) {
            bySub.computeIfAbsent(t.getSubcontractorId(), k -> new ArrayList<>()).add(t);
        }
        List<WorkloadRow> rows = new ArrayList<>();
        bySub.forEach((subId, tasks) -> counterparties.find(subId).ifPresent(sub -> rows.add(new WorkloadRow(sub, tasks,
                Workload.perWeek(tasks.stream().map(t -> new Workload.Span(t.getStart(), t.getEnd())).toList(), from, to)))));
        rows.sort((a, b) -> a.subcontractor().legalName().compareToIgnoreCase(b.subcontractor().legalName()));
        return rows;
    }

    public Optional<DealSummary> dealOf(Site site, Map<UUID, DealSummary> cache) {
        return deal(site.getDealId(), cache);
    }

    private Optional<DealSummary> deal(UUID id, Map<UUID, DealSummary> cache) {
        if (!cache.containsKey(id)) {
            cache.put(id, deals.find(id).orElse(null));
        }
        return Optional.ofNullable(cache.get(id));
    }

    private DealSummary deal(UUID dealId) {
        return deals.find(dealId).orElseThrow(() -> new NotFoundException("Deal " + dealId + " not found"));
    }

    private void requireSubcontractor(UUID id) {
        if (id == null) {
            return;
        }
        CounterpartySummary c = counterparties.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Counterparty " + id + " does not exist"));
        if (!c.hasRole("SUBCONTRACTOR") && !c.hasRole("SUPPLIER") && !c.hasRole("CARRIER")) {
            throw new IllegalArgumentException(c.legalName() + " is not a subcontractor, supplier or carrier");
        }
    }

    private static int progress(TaskData t) {
        return t.progressPct() == null ? 0 : t.progressPct();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
