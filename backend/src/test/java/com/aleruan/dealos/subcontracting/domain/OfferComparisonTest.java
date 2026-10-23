package com.aleruan.dealos.subcontracting.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferComparisonTest {

    private final UUID plaster = UUID.randomUUID();
    private final UUID paint = UUID.randomUUID();
    private final List<OfferComparison.Item> items = List.of(
            new OfferComparison.Item(plaster, new BigDecimal("100")),
            new OfferComparison.Item(paint, new BigDecimal("50")));

    @Test
    void cheapestCompleteOfferWinsIncompleteNeverRanksFirst() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID partial = UUID.randomUUID();
        OfferComparison.Result r = OfferComparison.compare(items, List.of(
                new OfferComparison.Offer(a, Map.of(plaster, new BigDecimal("45"), paint, new BigDecimal("10"))),   // 5000
                new OfferComparison.Offer(b, Map.of(plaster, new BigDecimal("40"), paint, new BigDecimal("30"))),   // 5500
                new OfferComparison.Offer(partial, Map.of(plaster, new BigDecimal("20")))));                       // 2000, incomplete
        assertEquals(a, r.cheapestCompleteOffer());
        assertEquals(new BigDecimal("5000.00"), r.totals().get(0).total());
        assertFalse(r.totals().get(2).complete());
        assertEquals(partial, r.cheapestOfferPerItem().get(plaster)); // per item the partial one is cheapest
        assertEquals(a, r.cheapestOfferPerItem().get(paint));
    }

    @Test
    void noOffersNoWinner() {
        OfferComparison.Result r = OfferComparison.compare(items, List.of());
        assertEquals(null, r.cheapestCompleteOffer());
        assertTrue(r.totals().isEmpty());
    }

    @Test
    void offerMustPriceEveryItemNonNegative() {
        assertTrue(OfferComparison.pricesAll(items, Map.of(plaster, BigDecimal.ONE, paint, BigDecimal.ZERO)));
        assertFalse(OfferComparison.pricesAll(items, Map.of(plaster, BigDecimal.ONE)));
        assertFalse(OfferComparison.pricesAll(items, Map.of(plaster, BigDecimal.ONE, paint, new BigDecimal("-1"))));
    }
}
