package com.aleruan.dealos.deal.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Deal lifecycle. Selling prices are frozen once the customer accepted; later changes go through change orders. */
public enum DealStatus {
    DRAFT, QUOTED, WON, LOST, IN_PROGRESS, COMPLETED, CANCELLED;

    private static final Map<DealStatus, Set<DealStatus>> NEXT = Map.of(
            DRAFT, EnumSet.of(QUOTED, CANCELLED),
            QUOTED, EnumSet.of(DRAFT, WON, LOST, CANCELLED),
            WON, EnumSet.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
            LOST, EnumSet.noneOf(DealStatus.class),
            COMPLETED, EnumSet.noneOf(DealStatus.class),
            CANCELLED, EnumSet.noneOf(DealStatus.class));

    public boolean canMoveTo(DealStatus target) {
        return NEXT.get(this).contains(target);
    }

    public Set<DealStatus> next() {
        return NEXT.get(this).isEmpty() ? EnumSet.noneOf(DealStatus.class) : EnumSet.copyOf(NEXT.get(this));
    }

    /** Quantities, markups and selling prices can change only before the customer accepted. */
    public boolean allowsPriceChanges() {
        return this == DRAFT || this == QUOTED;
    }

    /** Costs and subcontractors can still change while the deal is alive (e.g. after a tender). */
    public boolean allowsCostChanges() {
        return this != LOST && this != COMPLETED && this != CANCELLED;
    }
}
