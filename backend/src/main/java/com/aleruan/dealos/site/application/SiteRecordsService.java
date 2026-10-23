package com.aleruan.dealos.site.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.platform.files.FileStore;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.aleruan.dealos.site.domain.AcceptanceStageRepository;
import com.aleruan.dealos.site.domain.Defect;
import com.aleruan.dealos.site.domain.DefectRepository;
import com.aleruan.dealos.site.domain.DefectStatus;
import com.aleruan.dealos.site.domain.DiaryEntry;
import com.aleruan.dealos.site.domain.DiaryRepository;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Site diary, defects, warranty and the photos attached to them. */
@Service
public class SiteRecordsService {

    public static final String DIARY = "DiaryEntry";
    public static final String DEFECT = "Defect";
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final List<DefectStatus> UNRESOLVED = List.of(DefectStatus.OPEN, DefectStatus.FIXED);

    public record DiaryData(LocalDate entryDate, String weather, Integer workers, String text, UUID correctsId) {
    }

    private final SiteRepository sites;
    private final DiaryRepository diary;
    private final DefectRepository defects;
    private final AcceptanceStageRepository stages;
    private final CounterpartyLookup counterparties;
    private final FileStore files;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;

    SiteRecordsService(SiteRepository sites, DiaryRepository diary, DefectRepository defects,
                       AcceptanceStageRepository stages, CounterpartyLookup counterparties, FileStore files,
                       AuditService audit, CurrentActor actor, Clock clock) {
        this.sites = sites;
        this.diary = diary;
        this.defects = defects;
        this.stages = stages;
        this.counterparties = counterparties;
        this.files = files;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(PRAGUE));
    }

    public String actorName() {
        return actor.name();
    }

    // ---- handover and warranty ----

    @Transactional
    public Site setHandover(UUID siteId, LocalDate date, Integer warrantyMonths) {
        Site site = site(siteId);
        if (date != null && date.isAfter(today())) {
            throw new IllegalArgumentException("Handover is recorded once it has happened");
        }
        site.setHandover(date, date == null ? null : warrantyMonths == null ? 24 : warrantyMonths);
        defects.findBySiteIdOrderBySeqDesc(siteId).forEach(d -> d.applyHandover(date));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handoverDate", date);
        payload.put("warrantyMonths", site.getWarrantyMonths());
        audit.record("Site", siteId.toString(), "HANDOVER", payload);
        return site;
    }

    // ---- diary ----

    @Transactional(readOnly = true)
    public List<DiaryEntry> diary(UUID siteId) {
        site(siteId);
        return diary.findBySiteIdOrderByEntryDateDescCreatedAtDesc(siteId);
    }

    @Transactional
    public DiaryEntry addEntry(UUID siteId, DiaryData d) {
        site(siteId);
        if (d.correctsId() != null) {
            DiaryEntry old = entry(d.correctsId());
            if (!old.getSiteId().equals(siteId)) {
                throw new IllegalArgumentException("The corrected entry belongs to another site");
            }
        }
        DiaryEntry e = diary.save(new DiaryEntry(siteId, d.entryDate(), d.weather(), d.workers(), d.text(),
                d.correctsId(), actor.name(), clock.instant(), today()));
        audit.record(DIARY, e.getId().toString(), "CREATED", Map.of("siteId", siteId, "entry", d));
        return e;
    }

    @Transactional
    public DiaryEntry editEntry(UUID id, DiaryData d) {
        DiaryEntry e = entry(id);
        e.edit(d.entryDate(), d.weather(), d.workers(), d.text(), actor.name(), clock.instant(), today());
        audit.record(DIARY, id.toString(), "EDITED", d);
        return e;
    }

    public boolean editable(DiaryEntry e) {
        return e.editableBy(actor.name(), clock.instant());
    }

    // ---- defects ----

    @Transactional(readOnly = true)
    public List<Defect> defects(UUID siteId) {
        site(siteId);
        return defects.findBySiteIdOrderBySeqDesc(siteId);
    }

    /** Open and fixed-but-unchecked defects across all sites. */
    @Transactional(readOnly = true)
    public List<Defect> unresolved() {
        return defects.withStatus(UNRESOLVED);
    }

    @Transactional(readOnly = true)
    public long unresolvedCount(UUID siteId) {
        return defects.countBySiteIdAndStatusIn(siteId, UNRESOLVED);
    }

    @Transactional
    public Defect report(UUID siteId, Defect.Details d) {
        Site site = site(siteId);
        check(siteId, d);
        Defect defect = defects.save(new Defect(siteId, defects.lastSeq(siteId) + 1, d, site.getHandoverDate(),
                clock.instant(), actor.name()));
        audit.record(DEFECT, defect.getId().toString(), "REPORTED", Map.of("siteId", siteId, "seq", defect.getSeq(),
                "defect", d, "warranty", defect.isWarranty()));
        return defect;
    }

    @Transactional
    public Defect edit(UUID id, Defect.Details d) {
        Defect defect = defect(id);
        check(defect.getSiteId(), d);
        defect.edit(d, site(defect.getSiteId()).getHandoverDate());
        audit.record(DEFECT, id.toString(), "EDITED", d);
        return defect;
    }

    @Transactional
    public Defect move(UUID id, DefectStatus target, String note) {
        Defect defect = defect(id);
        DefectStatus from = defect.getStatus();
        defect.moveTo(target, note, actor.name(), clock.instant());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", target);
        payload.put("note", note);
        audit.record(DEFECT, id.toString(), "STATUS_CHANGED", payload);
        return defect;
    }

    @Transactional(readOnly = true)
    public Defect defect(UUID id) {
        return defects.findById(id).orElseThrow(() -> new NotFoundException("Defect " + id + " not found"));
    }

    // ---- photos ----

    @Transactional
    public FileInfo attach(String ownerType, UUID ownerId, String fileName, byte[] data) {
        owner(ownerType, ownerId);
        return files.store(ownerType, ownerId, fileName, data);
    }

    @Transactional
    public void detach(String ownerType, UUID ownerId, UUID fileId) {
        owner(ownerType, ownerId);
        files.delete(ownerType, ownerId, fileId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<FileInfo>> photos(String ownerType, List<UUID> ownerIds) {
        Map<UUID, List<FileInfo>> byOwner = new LinkedHashMap<>();
        for (FileInfo f : files.list(ownerType, ownerIds)) {
            byOwner.computeIfAbsent(f.ownerId(), k -> new java.util.ArrayList<>()).add(f);
        }
        return byOwner;
    }

    private void owner(String ownerType, UUID ownerId) {
        switch (ownerType) {
            case DIARY -> entry(ownerId);
            case DEFECT -> defect(ownerId);
            default -> throw new IllegalArgumentException("Unknown record type " + ownerType);
        }
    }

    private void check(UUID siteId, Defect.Details d) {
        if (d.subcontractorId() != null) {
            CounterpartySummary c = counterparties.find(d.subcontractorId())
                    .orElseThrow(() -> new IllegalArgumentException("Counterparty " + d.subcontractorId() + " does not exist"));
            if (!c.hasRole("SUBCONTRACTOR") && !c.hasRole("SUPPLIER")) {
                throw new IllegalArgumentException(c.legalName() + " is not a subcontractor or supplier");
            }
        }
        if (d.stageId() != null && stages.findById(d.stageId()).filter(s -> s.getSiteId().equals(siteId)).isEmpty()) {
            throw new IllegalArgumentException("The acceptance stage belongs to another site");
        }
    }

    private DiaryEntry entry(UUID id) {
        return diary.findById(id).orElseThrow(() -> new NotFoundException("Diary entry " + id + " not found"));
    }

    private Site site(UUID id) {
        return sites.findById(id).orElseThrow(() -> new NotFoundException("Site " + id + " not found"));
    }
}
