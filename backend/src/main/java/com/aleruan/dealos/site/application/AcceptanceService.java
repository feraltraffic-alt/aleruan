package com.aleruan.dealos.site.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.DealSummary;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.documents.CompanyProfile;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.PdfRenderer;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.AcceptanceStageRepository;
import com.aleruan.dealos.site.domain.ProtocolDocument;
import com.aleruan.dealos.site.domain.ProtocolHtml;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteTask;
import com.aleruan.dealos.site.domain.StageStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staged handover of a site to the customer and the protocols for it. */
@Service
public class AcceptanceService {

    public static final String AUDIT_TYPE = "AcceptanceStage";
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    public record StageData(String title, String description, BigDecimal amount, LocalDate plannedDate, Set<UUID> taskIds) {
    }

    public record Decision(StageStatus decision, String by, String remarks, LocalDate fixUntil) {
    }

    /** How the stages cover the contract: planned, accepted and what is not yet assigned to any stage. */
    public record Coverage(BigDecimal contractValue, BigDecimal planned, BigDecimal accepted, BigDecimal unassigned,
                           String currency) {
    }

    private final AcceptanceStageRepository stages;
    private final SiteService sites;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;
    private final PdfRenderer pdf;
    private final CompanyProfile company;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;

    AcceptanceService(AcceptanceStageRepository stages, SiteService sites, DealCatalog deals,
                      CounterpartyLookup counterparties, PdfRenderer pdf, CompanyProfile company, AuditService audit,
                      CurrentActor actor, Clock clock) {
        this.stages = stages;
        this.sites = sites;
        this.deals = deals;
        this.counterparties = counterparties;
        this.pdf = pdf;
        this.company = company;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AcceptanceStage> list(UUID siteId) {
        sites.get(siteId);
        return stages.findBySiteIdOrderBySeq(siteId);
    }

    @Transactional(readOnly = true)
    public Coverage coverage(UUID siteId) {
        Site site = sites.get(siteId);
        BigDecimal contract = deals.contractValue(site.getDealId());
        List<AcceptanceStage> all = stages.findBySiteIdOrderBySeq(siteId);
        BigDecimal planned = all.stream().map(AcceptanceStage::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal accepted = all.stream().filter(s -> s.getStatus().isAccepted()).map(AcceptanceStage::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = deals.find(site.getDealId()).map(DealSummary::currency).orElse(null);
        return new Coverage(contract, planned, accepted, contract.subtract(planned), currency);
    }

    @Transactional
    public AcceptanceStage create(UUID siteId, StageData d) {
        Site site = sites.get(siteId);
        AcceptanceStage stage = new AcceptanceStage(siteId, stages.lastSeq(siteId) + 1, clock.instant(), actor.name());
        stage.edit(d.title(), d.description(), d.amount(), d.plannedDate(), requireTasks(site, d.taskIds()));
        stages.save(stage);
        audit.record(AUDIT_TYPE, stage.getId().toString(), "CREATED", Map.of("siteId", siteId, "stage", d));
        return stage;
    }

    @Transactional
    public AcceptanceStage edit(UUID id, StageData d) {
        AcceptanceStage stage = get(id);
        stage.edit(d.title(), d.description(), d.amount(), d.plannedDate(), requireTasks(sites.get(stage.getSiteId()), d.taskIds()));
        audit.record(AUDIT_TYPE, id.toString(), "EDITED", d);
        return stage;
    }

    @Transactional
    public void remove(UUID id) {
        AcceptanceStage stage = get(id);
        if (stage.getStatus() != StageStatus.PLANNED) {
            throw new IllegalStateException("Only a planned stage can be deleted");
        }
        stages.delete(stage);
        audit.record(AUDIT_TYPE, id.toString(), "DELETED", Map.of("siteId", stage.getSiteId()));
    }

    @Transactional
    public AcceptanceStage submit(UUID id) {
        AcceptanceStage stage = get(id);
        stage.submit(clock.instant());
        audit.record(AUDIT_TYPE, id.toString(), "SUBMITTED", Map.of("amount", stage.getAmount()));
        return stage;
    }

    @Transactional
    public AcceptanceStage withdraw(UUID id) {
        AcceptanceStage stage = get(id);
        stage.withdraw();
        audit.record(AUDIT_TYPE, id.toString(), "WITHDRAWN", Map.of());
        return stage;
    }

    @Transactional
    public AcceptanceStage decide(UUID id, Decision d) {
        AcceptanceStage stage = get(id);
        stage.decide(d.decision(), d.by(), d.remarks(), d.fixUntil(), clock.instant());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decision", d.decision());
        payload.put("by", d.by());
        payload.put("remarks", d.remarks());
        payload.put("fixUntil", d.fixUntil());
        payload.put("amount", stage.getAmount());
        audit.record(AUDIT_TYPE, id.toString(), "DECIDED", payload);
        return stage;
    }

    @Transactional(readOnly = true)
    public AcceptanceStage get(UUID id) {
        return stages.findById(id).orElseThrow(() -> new NotFoundException("Stage " + id + " not found"));
    }

    /** The handover protocol for printing and signing; filled in once the customer has decided. */
    @Transactional(readOnly = true)
    public byte[] protocol(UUID id, DocLanguage requested) {
        AcceptanceStage stage = get(id);
        Site site = sites.get(stage.getSiteId());
        DealSummary deal = deals.find(site.getDealId())
                .orElseThrow(() -> new IllegalStateException("Deal of site " + site.getName() + " is missing"));
        CounterpartySummary customer = counterparties.find(deal.customerId())
                .orElseThrow(() -> new IllegalStateException("Customer of deal " + deal.number() + " is missing"));
        DocLanguage language = requested != null ? requested : DocLanguage.forCountry(customer.country());
        Map<UUID, SiteTask> tasks = site.getTasks().stream().collect(Collectors.toMap(SiteTask::getId, t -> t));
        List<String> works = stage.getTaskIds().stream().map(tasks::get).filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(SiteTask::getPosition)).map(SiteTask::getTitle).toList();
        String address = java.util.stream.Stream.of(site.getAddress(), site.getCity(), site.getCountry())
                .filter(java.util.Objects::nonNull).collect(Collectors.joining(", "));
        LocalDate today = LocalDate.now(clock.withZone(PRAGUE));
        ProtocolDocument doc = new ProtocolDocument(stage.protocolNumber(deal.number()), language,
                stage.getDecidedAt() != null ? stage.getDecidedAt().atZone(PRAGUE).toLocalDate() : today,
                new ProtocolDocument.Party(company.name(), company.country(), blank(company.companyId()),
                        blank(company.vatNumber()), blank(company.address())),
                new ProtocolDocument.Party(customer.legalName(), customer.country(), customer.companyId(),
                        customer.vatNumber(), customer.address()),
                site.getName(), address.isBlank() ? null : address, deal.number(), stage.getTitle(),
                stage.getDescription(), works, stage.getAmount(), deal.currency(), stage.getStatus(),
                stage.getDecidedBy(), stage.getDecidedAt() == null ? null : stage.getDecidedAt().atZone(PRAGUE).toLocalDate(),
                stage.getRemarks(), stage.getFixUntil(),
                site.getProjectManager() != null ? site.getProjectManager() : actor.name());
        return pdf.render(ProtocolHtml.render(doc, pdf.logo(), PdfRenderer.FONT));
    }

    private static Set<UUID> requireTasks(Site site, Set<UUID> taskIds) {
        if (taskIds == null) {
            return Set.of();
        }
        Set<UUID> known = site.getTasks().stream().map(SiteTask::getId).collect(Collectors.toSet());
        if (!known.containsAll(taskIds)) {
            throw new IllegalArgumentException("Some tasks are not on site " + site.getName());
        }
        return taskIds;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
