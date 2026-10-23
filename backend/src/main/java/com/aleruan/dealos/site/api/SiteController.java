package com.aleruan.dealos.site.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog.DealSummary;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteStatus;
import com.aleruan.dealos.site.domain.SiteTask;
import com.aleruan.dealos.site.domain.Workload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class SiteController {

    record EditBody(@NotBlank String name, String address, String city, String country, BigDecimal latitude,
                    BigDecimal longitude, String projectManager, String foreman, LocalDate plannedStart,
                    LocalDate plannedEnd, SiteStatus status, String notes) {
        Site.Details details() {
            return new Site.Details(name, address, city, country, latitude, longitude, projectManager, foreman,
                    plannedStart, plannedEnd, status, notes);
        }
    }

    record TaskBody(@NotBlank String title, String workType, UUID subcontractorId, @NotNull LocalDate start,
                    @NotNull LocalDate end, @Min(0) @Max(100) Integer progressPct) {
        SiteService.TaskData data() {
            return new SiteService.TaskData(title, workType, subcontractorId, start, end, progressPct);
        }
    }

    record PartyView(UUID id, String legalName, String country, String checkStatus) {
        static PartyView of(CounterpartySummary c) {
            return c == null ? null : new PartyView(c.id(), c.legalName(), c.country(), c.checkStatus());
        }
    }

    record TaskView(UUID id, int position, String title, String workType, UUID dealLineId, PartyView subcontractor,
                    LocalDate start, LocalDate end, int progressPct, boolean late) {
    }

    record SiteView(UUID id, UUID dealId, String dealNumber, String dealStatus, String name, String address,
                    String city, String country, BigDecimal latitude, BigDecimal longitude, String projectManager,
                    String foreman, LocalDate plannedStart, LocalDate plannedEnd, SiteStatus status, String notes,
                    PartyView customer, int progressPct, long lateTasks, Set<String> workTypes, Instant createdAt,
                    LocalDate handoverDate, Integer warrantyMonths, LocalDate warrantyUntil, long openDefects,
                    List<TaskView> tasks) {
    }

    record HandoverBody(LocalDate handoverDate, @Min(0) @Max(120) Integer warrantyMonths) {
    }

    record WorkloadTask(UUID taskId, String title, UUID siteId, String siteName, LocalDate start, LocalDate end,
                        int progressPct) {
    }

    record WorkloadRowView(PartyView subcontractor, List<WorkloadTask> tasks, List<Workload.Week> weeks) {
    }

    private final SiteService service;
    private final CounterpartyLookup counterparties;

    private final com.aleruan.dealos.site.application.SiteRecordsService records;

    SiteController(SiteService service, CounterpartyLookup counterparties,
                   com.aleruan.dealos.site.application.SiteRecordsService records) {
        this.service = service;
        this.counterparties = counterparties;
        this.records = records;
    }

    @PutMapping("/sites/{id}/handover")
    SiteView handover(@PathVariable UUID id, @Validated @RequestBody HandoverBody body) {
        return full(records.setHandover(id, body.handoverDate(), body.warrantyMonths()));
    }

    @GetMapping("/sites")
    List<SiteView> search(@RequestParam(required = false) SiteStatus status,
                          @RequestParam(required = false) String country,
                          @RequestParam(required = false) String city,
                          @RequestParam(required = false) String pm,
                          @RequestParam(required = false) UUID customerId,
                          @RequestParam(required = false) String workType,
                          @RequestParam(required = false) UUID dealId) {
        Map<UUID, DealSummary> deals = new HashMap<>();
        Map<UUID, CounterpartySummary> parties = new HashMap<>();
        return service.search(new SiteService.Filter(status, country, city, pm, customerId, workType, dealId)).stream()
                .map(s -> view(s, deals, parties, false)).toList();
    }

    @GetMapping("/sites/{id}")
    SiteView get(@PathVariable UUID id) {
        return full(service.get(id));
    }

    @PostMapping("/deals/{dealId}/site")
    SiteView create(@PathVariable UUID dealId) {
        return full(service.create(dealId));
    }

    @PutMapping("/sites/{id}")
    SiteView edit(@PathVariable UUID id, @Validated @RequestBody EditBody body) {
        return full(service.edit(id, body.details()));
    }

    @PostMapping("/sites/{id}/tasks")
    SiteView addTask(@PathVariable UUID id, @Validated @RequestBody TaskBody body) {
        return full(service.addTask(id, body.data()));
    }

    @PutMapping("/sites/{id}/tasks/{taskId}")
    SiteView updateTask(@PathVariable UUID id, @PathVariable UUID taskId, @Validated @RequestBody TaskBody body) {
        return full(service.updateTask(id, taskId, body.data()));
    }

    @DeleteMapping("/sites/{id}/tasks/{taskId}")
    SiteView removeTask(@PathVariable UUID id, @PathVariable UUID taskId) {
        return full(service.removeTask(id, taskId));
    }

    @PostMapping("/sites/{id}/tasks/from-deal")
    SiteView tasksFromDeal(@PathVariable UUID id) {
        return full(service.tasksFromDeal(id));
    }

    @GetMapping("/workload")
    List<WorkloadRowView> workload(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.workload(from, to).stream()
                .map(r -> new WorkloadRowView(PartyView.of(r.subcontractor()),
                        r.tasks().stream().map(t -> new WorkloadTask(t.getId(), t.getTitle(), t.getSite().getId(),
                                t.getSite().getName(), t.getStart(), t.getEnd(), t.getProgressPct())).toList(),
                        r.weeks()))
                .toList();
    }

    private SiteView full(Site s) {
        return view(s, new HashMap<>(), new HashMap<>(), true);
    }

    private SiteView view(Site s, Map<UUID, DealSummary> deals, Map<UUID, CounterpartySummary> parties, boolean withTasks) {
        DealSummary deal = service.dealOf(s, deals).orElse(null);
        LocalDate today = service.today();
        List<TaskView> tasks = withTasks ? s.getTasks().stream().map(t -> task(t, parties, today)).toList() : List.of();
        return new SiteView(s.getId(), s.getDealId(), deal == null ? null : deal.number(),
                deal == null ? null : deal.status(), s.getName(), s.getAddress(), s.getCity(), s.getCountry(),
                s.getLatitude(), s.getLongitude(), s.getProjectManager(), s.getForeman(), s.getPlannedStart(),
                s.getPlannedEnd(), s.getStatus(), s.getNotes(),
                deal == null ? null : PartyView.of(party(deal.customerId(), parties)),
                s.progressPct(), s.lateTasks(today), service.workTypes(s), s.getCreatedAt(), s.getHandoverDate(),
                s.getWarrantyMonths(), s.warrantyUntil(), records.unresolvedCount(s.getId()), tasks);
    }

    private TaskView task(SiteTask t, Map<UUID, CounterpartySummary> parties, LocalDate today) {
        return new TaskView(t.getId(), t.getPosition(), t.getTitle(), t.getWorkType(), t.getDealLineId(),
                t.getSubcontractorId() == null ? null : PartyView.of(party(t.getSubcontractorId(), parties)),
                t.getStart(), t.getEnd(), t.getProgressPct(), t.isLate(today));
    }

    private CounterpartySummary party(UUID id, Map<UUID, CounterpartySummary> cache) {
        if (!cache.containsKey(id)) {
            cache.put(id, counterparties.find(id).orElse(null));
        }
        return cache.get(id);
    }
}
