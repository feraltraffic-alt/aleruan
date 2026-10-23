package com.aleruan.dealos.subcontracting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Side-by-side comparison of offers, framework-free. An offer that does not price every item is
 * "incomplete" and is never ranked cheapest overall (it would look cheap by leaving things out).
 */
public final class OfferComparison {

    public record Item(UUID id, BigDecimal quantity) {
    }

    public record Offer(UUID id, Map<UUID, BigDecimal> unitPrices) {
    }

    public record OfferTotal(UUID offerId, BigDecimal total, boolean complete) {
    }

    public record Result(List<OfferTotal> totals, UUID cheapestCompleteOffer, Map<UUID, UUID> cheapestOfferPerItem) {
    }

    private OfferComparison() {
    }

    public static Result compare(List<Item> items, List<Offer> offers) {
        List<OfferTotal> totals = new ArrayList<>();
        for (Offer o : offers) {
            BigDecimal total = BigDecimal.ZERO;
            boolean complete = true;
            for (Item item : items) {
                BigDecimal price = o.unitPrices().get(item.id());
                if (price == null) {
                    complete = false;
                } else {
                    total = total.add(price.multiply(item.quantity()));
                }
            }
            totals.add(new OfferTotal(o.id(), total.setScale(2, RoundingMode.HALF_UP), complete));
        }
        UUID cheapest = totals.stream().filter(OfferTotal::complete)
                .min(Comparator.comparing(OfferTotal::total)).map(OfferTotal::offerId).orElse(null);

        Map<UUID, UUID> perItem = new LinkedHashMap<>();
        for (Item item : items) {
            offers.stream()
                    .filter(o -> o.unitPrices().get(item.id()) != null)
                    .min(Comparator.comparing(o -> o.unitPrices().get(item.id())))
                    .map(Offer::id)
                    .ifPresent(id -> perItem.put(item.id(), id));
        }
        totals.sort(Comparator.comparing(OfferTotal::complete).reversed().thenComparing(OfferTotal::total));
        return new Result(totals, cheapest, perItem);
    }

    /** True when the offer prices exactly the given items, each with a non-negative price. */
    public static boolean pricesAll(List<Item> items, Map<UUID, BigDecimal> unitPrices) {
        return items.stream().allMatch(i -> {
            BigDecimal p = unitPrices.get(i.id());
            return Objects.nonNull(p) && p.signum() >= 0;
        });
    }
}
