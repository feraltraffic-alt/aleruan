package com.aleruan.dealos.deal.application;

import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.platform.evidence.EvidenceCollector;
import com.aleruan.dealos.platform.evidence.EvidenceContributor;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.platform.files.FileStore;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The deal itself in the audit extract, and its contract papers. Once the customer said yes, a contract is expected. */
@Component
class DealEvidence implements EvidenceContributor {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final Set<DealStatus> AGREED = EnumSet.of(DealStatus.WON, DealStatus.IN_PROGRESS, DealStatus.COMPLETED);

    private final DealService deals;
    private final FileStore files;

    DealEvidence(DealService deals, FileStore files) {
        this.deals = deals;
        this.files = files;
    }

    @Override
    public void contribute(UUID dealId, EvidenceCollector out) {
        Deal deal = deals.get(dealId);
        out.audit(DealService.AUDIT_TYPE, dealId);
        List<FileInfo> docs = files.list(DealDocuments.OWNER, List.of(dealId));
        for (FileInfo f : docs) {
            UUID id = f.id();
            out.audit("File", id);
            out.entry(EvidenceEntry.of(EvidenceSection.CONTRACT, f.fileName())
                    .date(f.uploadedAt().atZone(PRAGUE).toLocalDate())
                    .by(f.uploadedBy())
                    .file(EvidenceFile.stored(f.fileName(), f.contentType(), f.sha256(), () -> files.data(id)))
                    .build());
        }
        if (!docs.isEmpty()) {
            out.ok("CONTRACT_ATTACHED", Map.of("count", Integer.toString(docs.size())));
        } else if (AGREED.contains(deal.getStatus())) {
            out.missing("CONTRACT_ATTACHED", Map.of());
        }
    }
}
