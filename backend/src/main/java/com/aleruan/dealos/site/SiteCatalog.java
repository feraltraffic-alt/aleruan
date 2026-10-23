package com.aleruan.dealos.site;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** How a site is doing, for analytics. */
    record SiteHealth(UUID siteId, UUID dealId, String name, String city, String country, String status,
                      LocalDate plannedEnd, int progressPct, long lateTasks, long openDefects, long overdueDefects,
                      LocalDate handoverDate) {
    }

    List<AcceptedStage> acceptedStages(UUID dealId);

    /** Every site, newest first. */
    List<SiteHealth> health();

    Optional<AcceptedStage> acceptedStage(UUID stageId);
}
