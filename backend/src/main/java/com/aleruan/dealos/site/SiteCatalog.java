package com.aleruan.dealos.site;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the site module for invoicing: accepted acceptance stages of a deal. */
public interface SiteCatalog {

    /** A stage the customer accepted; its amount is what may be invoiced for it. */
    record AcceptedStage(UUID id, UUID siteId, UUID dealId, int seq, String title, BigDecimal amount,
                         Instant acceptedAt, String acceptedBy, boolean withRemarks) {
    }

    List<AcceptedStage> acceptedStages(UUID dealId);

    Optional<AcceptedStage> acceptedStage(UUID stageId);
}
