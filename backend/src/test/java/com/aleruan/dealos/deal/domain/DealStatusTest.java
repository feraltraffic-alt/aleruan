package com.aleruan.dealos.deal.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DealStatusTest {

    @Test
    void happyPath() {
        assertTrue(DealStatus.DRAFT.canMoveTo(DealStatus.QUOTED));
        assertTrue(DealStatus.QUOTED.canMoveTo(DealStatus.WON));
        assertTrue(DealStatus.WON.canMoveTo(DealStatus.IN_PROGRESS));
        assertTrue(DealStatus.IN_PROGRESS.canMoveTo(DealStatus.COMPLETED));
    }

    @Test
    void quoteCanGoBackForRevisionButClosedDealsAreFinal() {
        assertTrue(DealStatus.QUOTED.canMoveTo(DealStatus.DRAFT));
        assertFalse(DealStatus.COMPLETED.canMoveTo(DealStatus.IN_PROGRESS));
        assertFalse(DealStatus.LOST.canMoveTo(DealStatus.QUOTED));
        assertFalse(DealStatus.DRAFT.canMoveTo(DealStatus.WON));
    }

    @Test
    void pricesFreezeAfterAcceptanceCostsStayOpenWhileAlive() {
        assertTrue(DealStatus.QUOTED.allowsPriceChanges());
        assertFalse(DealStatus.WON.allowsPriceChanges());
        assertTrue(DealStatus.IN_PROGRESS.allowsCostChanges());
        assertFalse(DealStatus.COMPLETED.allowsCostChanges());
    }
}
