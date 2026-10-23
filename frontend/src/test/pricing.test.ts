import { describe, expect, it } from 'vitest';
import { maxUnitCost, priceLine, totals } from '../pricing';

describe('pricing mirrors backend DealPricingTest', () => {
  it('uses the deal default markup', () => {
    expect(priceLine({ quantity: 10, unitCost: 500, markupPct: null, fixedUnitPrice: null }, 30))
      .toEqual({ unitPrice: 650, markupPct: 30, costTotal: 5000, priceTotal: 6500, margin: 1500 });
  });
  it('line markup overrides, fixed price wins and derives markup', () => {
    expect(priceLine({ quantity: 2, unitCost: 100, markupPct: 15, fixedUnitPrice: null }, 30).unitPrice).toBe(115);
    const fixed = priceLine({ quantity: 1, unitCost: 5000, markupPct: 10, fixedUnitPrice: 6500 }, 0);
    expect(fixed.unitPrice).toBe(6500);
    expect(fixed.markupPct).toBe(30);
  });
  it('totals: margin on price, markup on cost', () => {
    const t = totals([
      priceLine({ quantity: 1, unitCost: 5000, markupPct: null, fixedUnitPrice: null }, 30),
      priceLine({ quantity: 4, unitCost: 250, markupPct: 20, fixedUnitPrice: null }, 30),
    ]);
    expect(t).toEqual({ cost: 6000, price: 7700, margin: 1700, marginPct: 22.08, markupPct: 28.33 });
    expect(totals([]).marginPct).toBeNull();
  });
  it('reverse calculation rounds down', () => {
    expect(maxUnitCost(6500, 30)).toBe(5000);
    expect(maxUnitCost(11400, 29.96)).toBe(8771.92);
  });
});
