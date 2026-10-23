/** Parses "1 234,5" or "1234.5"; NaN when empty or invalid. */
export const parseNum = (s: string) => (s.trim() === '' ? NaN : Number(s.replace(/\s/g, '').replace(',', '.')));

/** Share of the selling price that stays with Aleruan for a given markup on cost. */
export const marginOnPrice = (markupPct: number) => (markupPct <= -100 ? null : (markupPct * 100) / (100 + markupPct));
