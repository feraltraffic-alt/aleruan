package com.aleruan.dealos.site.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** A stage is planned, handed over for inspection, then accepted (with or without remarks) or rejected. */
public enum StageStatus {
    PLANNED, SUBMITTED, ACCEPTED, ACCEPTED_WITH_REMARKS, REJECTED;

    private static final Map<StageStatus, Set<StageStatus>> NEXT = Map.of(
            PLANNED, EnumSet.of(SUBMITTED),
            SUBMITTED, EnumSet.of(ACCEPTED, ACCEPTED_WITH_REMARKS, REJECTED, PLANNED),
            REJECTED, EnumSet.of(SUBMITTED),
            ACCEPTED, EnumSet.noneOf(StageStatus.class),
            ACCEPTED_WITH_REMARKS, EnumSet.noneOf(StageStatus.class));

    public boolean canMoveTo(StageStatus target) {
        return NEXT.get(this).contains(target);
    }

    public Set<StageStatus> next() {
        return NEXT.get(this).isEmpty() ? EnumSet.noneOf(StageStatus.class) : EnumSet.copyOf(NEXT.get(this));
    }

    public boolean isAccepted() {
        return this == ACCEPTED || this == ACCEPTED_WITH_REMARKS;
    }
}
