package com.aleruan.dealos.site.application;

import com.aleruan.dealos.site.SiteCatalog;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.AcceptanceStageRepository;
import com.aleruan.dealos.site.domain.SiteRepository;
import com.aleruan.dealos.site.domain.StageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SiteCatalogImpl implements SiteCatalog {

    private final SiteRepository sites;
    private final AcceptanceStageRepository stages;

    SiteCatalogImpl(SiteRepository sites, AcceptanceStageRepository stages) {
        this.sites = sites;
        this.stages = stages;
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
