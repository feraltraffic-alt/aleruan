/**
 * Mirror of the backend's DealPricing, used for live previews while typing.
 * The server's numbers are the source of truth; tests pin both to the same cases.
 */
const round2 = (v: number) => Math.round((v + Number.EPSILON) * 100) / 100;

export interface LineInput {
  quantity: number;
  unitCost: number;
  markupPct: number | null;
  fixedUnitPrice: number | null;
}

export interface PricedLine {
  unitPrice: number;
  markupPct: number | null;
  costTotal: number;
  priceTotal: number;
  margin: number;
}

export function priceLine(l: LineInput, defaultMarkupPct: number): PricedLine {
  const unitCost = round2(l.unitCost);
  let unitPrice: number;
  let markupPct: number | null;
  if (l.fixedUnitPrice != null) {
    unitPrice = round2(l.fixedUnitPrice);
    markupPct = unitCost === 0 ? null : round2(((unitPrice - unitCost) * 100) / unitCost);
  } else {
    markupPct = l.markupPct ?? defaultMarkupPct;
    unitPrice = round2(unitCost * (1 + markupPct / 100));
  }
  const costTotal = round2(unitCost * l.quantity);
  const priceTotal = round2(unitPrice * l.quantity);
  return { unitPrice, markupPct, costTotal, priceTotal, margin: round2(priceTotal - costTotal) };
}

export interface Totals {
  cost: number;
  price: number;
  margin: number;
  marginPct: number | null;
  markupPct: number | null;
}

export function totals(lines: PricedLine[]): Totals {
  const cost = round2(lines.reduce((s, l) => s + l.costTotal, 0));
  const price = round2(lines.reduce((s, l) => s + l.priceTotal, 0));
  const margin = round2(price - cost);
  return {
    cost, price, margin,
    marginPct: price === 0 ? null : round2((margin * 100) / price),
    markupPct: cost === 0 ? null : round2((margin * 100) / cost),
  };
}

/** Highest unit cost that keeps the markup within the customer's unit price (rounded down). */
export function maxUnitCost(unitPrice: number, markupPct: number): number {
  return Math.floor((unitPrice / (1 + markupPct / 100)) * 100 + 1e-9) / 100;
}
