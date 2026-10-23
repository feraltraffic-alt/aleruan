package com.aleruan.dealos.deal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DealPricingTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void lineUsesDealDefaultMarkup() {
        DealPricing.PricedLine p = DealPricing.price(new DealPricing.LineInput(d("10"), d("500"), null, null), d("30"));
        assertEquals(d("650.00"), p.unitPrice());
        assertEquals(d("5000.00"), p.costTotal());
        assertEquals(d("6500.00"), p.priceTotal());
        assertEquals(d("1500.00"), p.margin());
        assertEquals(d("30"), p.markupPct());
    }

    @Test
    void lineMarkupOverridesDefault() {
        DealPricing.PricedLine p = DealPricing.price(new DealPricing.LineInput(d("2"), d("100"), d("15"), null), d("30"));
        assertEquals(d("115.00"), p.unitPrice());
    }

    @Test
    void fixedSellingPriceWinsAndMarkupIsDerived() {
        // Protocol 07/10/2024: plastering sold at 6 500 EUR; if the subcontractor costs 5 000, markup is 30 %.
        DealPricing.PricedLine p = DealPricing.price(new DealPricing.LineInput(d("1"), d("5000"), d("10"), d("6500")), d("0"));
        assertEquals(d("6500.00"), p.unitPrice());
        assertEquals(d("30.00"), p.markupPct());
    }

    @Test
    void totalsGiveMarginOnPriceAndMarkupOnCost() {
        DealPricing.Totals t = DealPricing.total(List.of(
                DealPricing.price(new DealPricing.LineInput(d("1"), d("5000"), null, null), d("30")),
                DealPricing.price(new DealPricing.LineInput(d("4"), d("250"), d("20"), null), d("30"))));
        assertEquals(d("6000.00"), t.cost());
        assertEquals(d("7700.00"), t.price());
        assertEquals(d("1700.00"), t.margin());
        assertEquals(d("22.08"), t.marginPct());  // 1700 / 7700
        assertEquals(d("28.33"), t.markupPct());  // 1700 / 6000
    }

    @Test
    void emptyDealHasNoPercentages() {
        DealPricing.Totals t = DealPricing.total(List.of());
        assertNull(t.marginPct());
        assertNull(t.markupPct());
    }

    @Test
    void reverseCalculationFromCustomerPrice() {
        assertEquals(d("5000.00"), DealPricing.maxUnitCost(d("6500"), d("30")));
        assertEquals(d("8771.92"), DealPricing.maxUnitCost(d("11400"), d("29.96"))); // rounded down: never above the price
    }

    @Test
    void negativeMarginIsVisible() {
        DealPricing.PricedLine p = DealPricing.price(new DealPricing.LineInput(d("1"), d("120"), null, d("100")), d("0"));
        assertEquals(d("-20.00"), p.margin());
    }
}
