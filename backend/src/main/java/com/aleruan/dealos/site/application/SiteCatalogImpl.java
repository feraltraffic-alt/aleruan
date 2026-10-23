package com.aleruan.dealos.site.application;

import com.aleruan.dealos.site.SiteCatalog;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.AcceptanceStageRepository;
import com.aleruan.dealos.site.domain.Defect;
import com.aleruan.dealos.site.domain.DefectRepository;
import com.aleruan.dealos.site.domain.DefectStatus;
import com.aleruan.dealos.site.domain.Site;
import com.aleruan.dealos.site.domain.SiteRepository;
import com.aleruan.dealos.site.domain.StageStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SiteCatalogImpl implements SiteCatalog {

    private static final java.time.ZoneId PRAGUE = java.time.ZoneId.of("Europe/Prague");

    private final SiteRepository sites;
    private final AcceptanceStageRepository stages;
    private final DefectRepository defects;
    private final java.time.Clock clock;

    SiteCatalogImpl(SiteRepository sites, AcceptanceStageRepository stages, DefectRepository defects,
                    java.time.Clock clock) {
        this.sites = sites;
        this.stages = stages;
        this.defects = defects;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteHealth> health() {
        java.time.LocalDate today = java.time.LocalDate.now(clock.withZone(PRAGUE));
        Map<UUID, List<Defect>> open = defects.withStatus(List.of(DefectStatus.OPEN, DefectStatus.FIXED)).stream()
                .collect(java.util.stream.Collectors.groupingBy(Defect::getSiteId));
        return sites.findAll().stream()
                .sorted(java.util.Comparator.comparing(Site::getCreatedAt).reversed())
                .map(s -> {
                    List<Defect> d = open.getOrDefault(s.getId(), List.of());
                    return new SiteHealth(s.getId(), s.getDealId(), s.getName(), s.getCity(), s.getCountry(),
                            s.getStatus().name(), s.getPlannedEnd(), s.progressPct(), s.lateTasks(today), d.size(),
                            d.stream().filter(x -> x.isOverdue(today)).count(), s.getHandoverDate());
                }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcceptedStage> acceptedStages(UUID dealId) {
        return sites.findByDealId(dealId)
                .map(site -> stages.findBySiteIdOrderBySeq(site.getId()).stream()
                        .filter(s -> s.getStatus().isAccepted())
                        .map(s -> summary(s, dealId)).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AcceptedStage> acceptedStage(UUID stageId) {
        return stages.findById(stageId).filter(s -> s.getStatus().isAccepted())
                .flatMap(s -> sites.findById(s.getSiteId()).map(site -> summary(s, site.getDealId())));
    }

    private static AcceptedStage summary(AcceptanceStage s, UUID dealId) {
        return new AcceptedStage(s.getId(), s.getSiteId(), dealId, s.getSeq(), s.getTitle(), s.getAmount(),
                s.getDecidedAt(), s.getDecidedBy(), s.getStatus() == StageStatus.ACCEPTED_WITH_REMARKS);
    }
}
