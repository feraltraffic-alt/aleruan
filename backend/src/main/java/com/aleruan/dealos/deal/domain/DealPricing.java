package com.aleruan.dealos.deal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Margin arithmetic, framework-free. A line is priced either by markup on cost (line markup, else the deal's
 * default) or by a fixed selling price agreed with the customer, in which case the markup is derived.
 * Money is rounded to cents per line, the same way an invoice rounds it.
 */
public final class DealPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY = 2;
    private static final int PERCENT = 2;

    private DealPricing() {
    }

    /** {@code markupPct} and {@code fixedUnitPrice} are optional; a fixed price wins over any markup. */
    public record LineInput(BigDecimal quantity, BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice) {
    }

    public record PricedLine(BigDecimal quantity, BigDecimal unitCost, BigDecimal markupPct, BigDecimal unitPrice,
                             BigDecimal costTotal, BigDecimal priceTotal, BigDecimal margin) {
    }

    /** marginPct = margin / price (what the business keeps); markupPct = margin / cost. Null when undefined. */
    public record Totals(BigDecimal cost, BigDecimal price, BigDecimal margin, BigDecimal marginPct, BigDecimal markupPct) {
    }

    public static PricedLine price(LineInput in, BigDecimal defaultMarkupPct) {
        BigDecimal qty = nz(in.quantity());
        BigDecimal unitCost = money(nz(in.unitCost()));
        BigDecimal unitPrice;
        BigDecimal markup;
        if (in.fixedUnitPrice() != null) {
            unitPrice = money(in.fixedUnitPrice());
            markup = markupFor(unitCost, unitPrice);
        } else {
            markup = in.markupPct() != null ? in.markupPct() : nz(defaultMarkupPct);
            unitPrice = money(unitCost.multiply(BigDecimal.ONE.add(markup.divide(HUNDRED, 10, RoundingMode.HALF_UP))));
        }
        BigDecimal costTotal = money(unitCost.multiply(qty));
        BigDecimal priceTotal = money(unitPrice.multiply(qty));
        return new PricedLine(qty, unitCost, markup, unitPrice, costTotal, priceTotal, priceTotal.subtract(costTotal));
    }

    public static Totals total(List<PricedLine> lines) {
        BigDecimal cost = BigDecimal.ZERO.setScale(MONEY);
        BigDecimal price = BigDecimal.ZERO.setScale(MONEY);
        for (PricedLine l : lines) {
            cost = cost.add(l.costTotal());
            price = price.add(l.priceTotal());
        }
        BigDecimal margin = price.subtract(cost);
        BigDecimal marginPct = price.signum() == 0 ? null : pct(margin, price);
        BigDecimal markupPct = cost.signum() == 0 ? null : pct(margin, cost);
        return new Totals(cost, price, margin, marginPct, markupPct);
    }

    /** Highest unit cost that still leaves {@code markupPct} on top of it within the customer's unit price. */
    public static BigDecimal maxUnitCost(BigDecimal unitPrice, BigDecimal markupPct) {
        return unitPrice.divide(BigDecimal.ONE.add(markupPct.divide(HUNDRED, 10, RoundingMode.HALF_UP)), MONEY,
                RoundingMode.DOWN);
    }

    /** Markup in percent that turns {@code unitCost} into {@code unitPrice}; null when the cost is zero. */
    public static BigDecimal markupFor(BigDecimal unitCost, BigDecimal unitPrice) {
        if (unitCost.signum() == 0) {
            return null;
        }
        return pct(unitPrice.subtract(unitCost), unitCost);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, PERCENT, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
