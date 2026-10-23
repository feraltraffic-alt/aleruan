package com.aleruan.dealos.deal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the deal module (used by subcontracting for tenders and awards). */
public interface DealCatalog {

    record DealSummary(UUID id, String number, String title, String currency, String status, String siteCountry,
                       UUID customerId, String direction) {
    }

    /** What the customer is offered for one line: no costs, no subcontractors. */
    record CustomerLine(int position, String kind, String description, String workType, String unit,
                        BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    record LineSummary(UUID id, int position, String kind, String description, String workType, String unit,
                       BigDecimal quantity, BigDecimal unitCost, UUID subcontractorId) {
    }

    /** A change order as the customer sees it: prices only. */
    record ChangeOrderSummary(UUID id, UUID dealId, String number, int seq, String title, String reason, String status,
                              int extraDays, List<CustomerLine> lines, BigDecimal total, Instant decidedAt,
                              String decidedBy) {
    }

    Optional<DealSummary> find(UUID dealId);

    Optional<ChangeOrderSummary> changeOrder(UUID changeOrderId);

    /** All change orders of the deal, in order. */
    List<ChangeOrderSummary> changeOrders(UUID dealId);

    /** Agreed value for the customer: the deal's lines plus approved change orders. */
    BigDecimal contractValue(UUID dealId);

    List<LineSummary> lines(UUID dealId);

    List<CustomerLine> customerLines(UUID dealId);

    /**
     * A quote went out: a draft becomes "quoted"; a quoted deal stays quoted.
     * Fails once the customer accepted, because prices are frozen then.
     */
    void markQuoted(UUID dealId);

    /** Tender result: set the line's unit cost and subcontractor (selling price unchanged). */
    void assignCost(UUID dealId, UUID lineId, BigDecimal unitCost, UUID subcontractorId, String reason);
}
