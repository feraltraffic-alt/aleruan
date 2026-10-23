package com.aleruan.dealos.deal.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** A change order is drafted, sent to the customer, then approved or rejected; it can go back to draft for revision. */
public enum ChangeOrderStatus {
    DRAFT, SENT, APPROVED, REJECTED, CANCELLED;

    private static final Map<ChangeOrderStatus, Set<ChangeOrderStatus>> NEXT = Map.of(
            DRAFT, EnumSet.of(SENT, CANCELLED),
            SENT, EnumSet.of(APPROVED, REJECTED, DRAFT, CANCELLED),
            APPROVED, EnumSet.noneOf(ChangeOrderStatus.class),
            REJECTED, EnumSet.noneOf(ChangeOrderStatus.class),
            CANCELLED, EnumSet.noneOf(ChangeOrderStatus.class));

    public boolean canMoveTo(ChangeOrderStatus target) {
        return NEXT.get(this).contains(target);
    }

    public Set<ChangeOrderStatus> next() {
        return NEXT.get(this).isEmpty() ? EnumSet.noneOf(ChangeOrderStatus.class) : EnumSet.copyOf(NEXT.get(this));
    }

    /** What the customer is asked to pay is fixed once the change order was sent. */
    public boolean allowsPriceChanges() {
        return this == DRAFT;
    }

    /** Costs and subcontractors may still change after approval (e.g. a cheaper crew is found). */
    public boolean allowsCostChanges() {
        return this == DRAFT || this == SENT || this == APPROVED;
    }
}
