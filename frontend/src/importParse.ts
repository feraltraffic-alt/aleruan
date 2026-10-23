/** Parsing of what was read from a document, the same way the server's ImportChecks does. */

/** Dates as the server accepts them: ISO, or day.month.year as printed on Czech documents. */
export const isoDay = (s: string | null | undefined) => {
  const v = (s ?? '').trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return v;
  const m = /^(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})$/.exec(v);
  return m ? `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}` : '';
};

/** Amounts as the server parses them: "12345.50", "12 345,50", "12.345,50", "1,234.50". */
export const amount = (s: string) => {
  let v = s.replace(/[\s\u00a0]/g, '').replace(/[^0-9,.-]/g, '');
  if (!v) return s.trim() ? NaN : null;
  const comma = v.lastIndexOf(',');
  const dot = v.lastIndexOf('.');
  if (comma >= 0 && dot >= 0) v = comma > dot ? v.replace(/\./g, '').replace(',', '.') : v.replace(/,/g, '');
  else if (comma >= 0) v = v.replace(',', '.');
  const n = Number(v);
  return Number.isFinite(n) ? Math.round(n * 100) / 100 : NaN;
};
