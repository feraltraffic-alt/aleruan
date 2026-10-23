/** Same rules as the backend's TaxIdentifiers: catch typos before the request is sent. */

export const normalize = (raw: string) => raw.toUpperCase().replace(/[\s.\-/]/g, '');

export function isValidCzIco(raw: string): boolean {
  const ico = normalize(raw);
  if (!/^\d{8}$/.test(ico)) return false;
  let sum = 0;
  for (let i = 0; i < 7; i++) sum += Number(ico[i]) * (8 - i);
  const r = sum % 11;
  const check = r === 0 ? 1 : r === 1 ? 0 : 11 - r;
  return check === Number(ico[7]);
}

const NIP_WEIGHTS = [6, 5, 7, 2, 3, 4, 5, 6, 7];

export function isValidPlNip(raw: string): boolean {
  const nip = normalize(raw);
  if (!/^\d{10}$/.test(nip)) return false;
  const check = NIP_WEIGHTS.reduce((s, w, i) => s + w * Number(nip[i]), 0) % 11;
  return check !== 10 && check === Number(nip[9]);
}

export const isPlausibleVat = (raw: string) => /^[A-Z]{2}[0-9A-Z]{2,12}$/.test(normalize(raw));

/** Error key for the company id in this country, or null if it looks fine. */
export function companyIdError(country: string, companyId: string): 'form.err.ico' | 'form.err.nip' | null {
  if (!companyId.trim()) return null;
  if (country === 'CZ' && !isValidCzIco(companyId)) return 'form.err.ico';
  if (country === 'PL' && !isValidPlNip(companyId)) return 'form.err.nip';
  return null;
}
