package com.aleruan.dealos.deal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the deal module (used by subcontracting for tenders and awards). */
public interface DealCatalog {

    record DealSummary(UUID id, String number, String title, String currency, String status, String siteCountry) {
    }

    record LineSummary(UUID id, int position, String kind, String description, String workType, String unit,
                       BigDecimal quantity, BigDecimal unitCost, UUID subcontractorId) {
    }

    Optional<DealSummary> find(UUID dealId);

    List<LineSummary> lines(UUID dealId);

    /** Tender result: set the line's unit cost and subcontractor (selling price unchanged). */
    void assignCost(UUID dealId, UUID lineId, BigDecimal unitCost, UUID subcontractorId, String reason);
}
