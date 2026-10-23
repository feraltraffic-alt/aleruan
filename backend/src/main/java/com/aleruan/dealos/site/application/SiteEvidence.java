package com.aleruan.dealos.site.application;

import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.DealSummary;
import com.aleruan.dealos.platform.evidence.EvidenceCollector;
import com.aleruan.dealos.platform.evidence.EvidenceContributor;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.platform.files.FileStore;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.AcceptanceStageRepository;
import com.aleruan.dealos.site.domain.Defect;
import com.aleruan.dealos.site.domain.DefectRepository;
import com.aleruan.dealos.site.domain.DiaryEntry;
import com.aleruan.dealos.site.domain.DiaryRepository;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteRepository;
import com.aleruan.dealos.site.domain.StageStatus;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * What happened on site: acceptance protocols, the site diary and defects with their photos. Checks that
 * the accepted stages cover the contract, the handover is recorded, no defect is left open and a diary is kept.
 */
@Component
class SiteEvidence implements EvidenceContributor {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final Set<String> RUNNING = Set.of("WON", "IN_PROGRESS", "COMPLETED");

    private final SiteRepository sites;
    private final AcceptanceStageRepository stages;
    private final DiaryRepository diary;
    private final DefectRepository defects;
    private final AcceptanceService acceptance;
    private final FileStore files;
    private final DealCatalog deals;

    SiteEvidence(SiteRepository sites, AcceptanceStageRepository stages, DiaryRepository diary, DefectRepository defects,
                 AcceptanceService acceptance, FileStore files, DealCatalog deals) {
        this.sites = sites;
        this.stages = stages;
        this.diary = diary;
        this.defects = defects;
        this.acceptance = acceptance;
        this.files = files;
        this.deals = deals;
    }

    @Override
    public void contribute(UUID dealId, EvidenceCollector out) {
        Optional<DealSummary> deal = deals.find(dealId);
        Optional<Site> found = sites.findByDealId(dealId);
        if (found.isEmpty()) {
            if (deal.map(d -> "CONSTRUCTION".equals(d.direction()) && RUNNING.contains(d.status())).orElse(false)) {
                out.warn("SITE_NOT_OPENED", Map.of());
            }
            return;
        }
        Site site = found.get();
        String currency = deal.map(DealSummary::currency).orElse(null);
        String dealNumber = deal.map(DealSummary::number).orElse("");
        out.audit(SiteService.AUDIT_TYPE, site.getId());

        stages(site, dealId, dealNumber, currency, out);
        diary(site, out);
        defects(site, out);

        if (site.getHandoverDate() != null) {
            out.ok("HANDOVER", Map.of("date", site.getHandoverDate().toString()));
        } else {
            out.warn("HANDOVER", Map.of());
        }
    }

    private void stages(Site site, UUID dealId, String dealNumber, String currency, EvidenceCollector out) {
        BigDecimal accepted = BigDecimal.ZERO;
        for (AcceptanceStage s : stages.findBySiteIdOrderBySeq(site.getId())) {
            out.audit(AcceptanceService.AUDIT_TYPE, s.getId());
            if (s.getStatus() == StageStatus.PLANNED || s.getStatus() == StageStatus.SUBMITTED) {
                continue;
            }
            if (s.getStatus().isAccepted()) {
                accepted = accepted.add(s.getAmount());
            }
            UUID id = s.getId();
            String number = s.protocolNumber(dealNumber);
            out.entry(EvidenceEntry.of(EvidenceSection.ACCEPTANCE, number + " " + s.getTitle())
                    .date(s.getDecidedAt() == null ? null : s.getDecidedAt().atZone(PRAGUE).toLocalDate())
                    .by(s.getDecidedBy())
                    .status(s.getStatus().name())
                    .amount(s.getAmount(), currency)
                    .reference(number)
                    .text(s.getRemarks())
                    .file(EvidenceFile.pdf(number + ".pdf", null, () -> acceptance.protocol(id, null)))
                    .build());
        }
        BigDecimal contract = deals.contractValue(dealId);
        Map<String, String> params = Map.of("accepted", accepted.toPlainString(), "contract", contract.toPlainString(),
                "currency", currency == null ? "" : currency);
        if (contract.signum() > 0 && accepted.compareTo(contract) >= 0) {
            out.ok("ACCEPTANCE_COVERAGE", params);
        } else {
            out.warn("ACCEPTANCE_COVERAGE", params);
        }
    }

    private void diary(Site site, EvidenceCollector out) {
        List<DiaryEntry> entries = diary.findBySiteIdOrderByEntryDateDescCreatedAtDesc(site.getId()).stream()
                .sorted(Comparator.comparing(DiaryEntry::getEntryDate).thenComparing(DiaryEntry::getCreatedAt))
                .toList();
        Map<UUID, List<FileInfo>> photos = photos(SiteRecordsService.DIARY, entries.stream().map(DiaryEntry::getId).toList());
        for (DiaryEntry e : entries) {
            out.audit(SiteRecordsService.DIARY, e.getId());
            out.entry(EvidenceEntry.of(EvidenceSection.DIARY, e.getWeather() == null ? "" : e.getWeather())
                    .date(e.getEntryDate())
                    .by(e.getAuthor())
                    .text(e.getText())
                    .files(files(e.getEntryDate() + "-", photos.getOrDefault(e.getId(), List.of()), out))
                    .build());
        }
        if (entries.isEmpty()) {
            out.warn("DIARY_KEPT", Map.of());
        } else {
            out.ok("DIARY_KEPT", Map.of("count", Integer.toString(entries.size())));
        }
    }

    private void defects(Site site, EvidenceCollector out) {
        List<Defect> list = defects.findBySiteIdOrderBySeqDesc(site.getId()).stream()
                .sorted(Comparator.comparingInt(Defect::getSeq)).toList();
        Map<UUID, List<FileInfo>> photos = photos(SiteRecordsService.DEFECT, list.stream().map(Defect::getId).toList());
        long open = 0;
        for (Defect d : list) {
            out.audit(SiteRecordsService.DEFECT, d.getId());
            if (d.getStatus().isOpen()) {
                open++;
            }
            String text = Stream.of(d.getLocation(), d.getDescription(), d.getFixNote())
                    .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining("\n"));
            out.entry(EvidenceEntry.of(EvidenceSection.DEFECTS, d.getSeq() + ". " + d.getTitle())
                    .date(d.getFoundOn())
                    .by(d.getCreatedBy())
                    .status(d.getStatus().name())
                    .text(text.isBlank() ? null : text)
                    .files(files("defect-" + d.getSeq() + "-", photos.getOrDefault(d.getId(), List.of()), out))
                    .build());
        }
        if (open > 0) {
            out.warn("DEFECTS_OPEN", Map.of("count", Long.toString(open)));
        } else {
            out.ok("DEFECTS_OPEN", Map.of("count", "0"));
        }
    }

    private Map<UUID, List<FileInfo>> photos(String ownerType, List<UUID> ownerIds) {
        return files.list(ownerType, ownerIds).stream().collect(Collectors.groupingBy(FileInfo::ownerId));
    }

    private List<EvidenceFile> files(String prefix, List<FileInfo> infos, EvidenceCollector out) {
        return infos.stream().map(f -> {
            out.audit("File", f.id());
            UUID id = f.id();
            return new EvidenceFile(prefix + f.fileName(), f.contentType(), f.sha256(), () -> files.data(id));
        }).toList();
    }
}
