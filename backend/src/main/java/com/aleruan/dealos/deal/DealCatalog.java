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
                              String decidedBy, Instant sentAt, String createdBy) {
    }

    /** One priced line for analytics: the deal's own lines and lines of approved change orders. */
    record EconomicsLine(String kind, String workType, UUID subcontractorId, BigDecimal cost, BigDecimal price,
                         boolean changeOrder) {
    }

    /**
     * A deal's economics in its own currency: what it is agreed to earn and cost, line by line, plus change
     * orders sent to the customer and not yet decided.
     */
    record DealEconomics(UUID id, String number, String title, String status, String direction, String siteCountry,
                         String currency, UUID customerId, Instant createdAt, List<EconomicsLine> lines,
                         int pendingChangeOrders, BigDecimal pendingChangePrice) {
    }

    Optional<DealSummary> find(UUID dealId);

    /** Economics of every deal in one of the given statuses (e.g. WON, IN_PROGRESS, COMPLETED). */
    List<DealEconomics> economics(java.util.Set<String> statuses);

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
