import type { QuoteLanguage, VatMode, VatReason } from './api/types';

/** Mirror of the backend's VatAdvisor, used by the demo API. */
const EU = new Set(['AT', 'BE', 'BG', 'HR', 'CY', 'CZ', 'DK', 'EE', 'FI', 'FR', 'DE', 'GR', 'EL', 'HU', 'IE', 'IT',
  'LV', 'LT', 'LU', 'MT', 'NL', 'PL', 'PT', 'RO', 'SK', 'SI', 'ES', 'SE']);

export interface VatSuggestion { mode: VatMode; rate: number; reason: VatReason }

export function suggestVat(customerCountry: string | null, customerVat: string | null, siteCountry: string | null,
  construction: boolean): VatSuggestion {
  const customer = customerCountry?.toUpperCase() ?? null;
  const site = siteCountry?.trim() ? siteCountry.toUpperCase() : null;
  const payer = !!customerVat?.trim();
  if (customer && !EU.has(customer)) return { mode: 'OUTSIDE_SCOPE', rate: 0, reason: 'CUSTOMER_OUTSIDE_EU' };
  if (construction && site && site !== 'CZ') {
    return payer && EU.has(site) ? { mode: 'REVERSE_CHARGE_EU', rate: 0, reason: 'SITE_IN_OTHER_EU_STATE' }
      : { mode: 'STANDARD', rate: 21, reason: 'CHECK_WITH_ACCOUNTANT' };
  }
  if (customer && customer !== 'CZ') {
    return payer ? { mode: 'REVERSE_CHARGE_EU', rate: 0, reason: 'EU_BUSINESS_CUSTOMER' }
      : { mode: 'STANDARD', rate: 21, reason: 'CHECK_WITH_ACCOUNTANT' };
  }
  if (construction && payer) return { mode: 'REVERSE_CHARGE_CZ', rate: 0, reason: 'CZ_CONSTRUCTION_FOR_VAT_PAYER' };
  return { mode: 'STANDARD', rate: 21, reason: 'DOMESTIC_STANDARD' };
}

export function languageFor(country: string | null): QuoteLanguage {
  switch (country?.toUpperCase()) {
    case 'CZ': case 'SK': return 'CS';
    case 'PL': return 'PL';
    case 'DE': case 'AT': case 'LI': return 'DE';
    default: return 'EN';
  }
}

/** VAT on the subtotal, rounded half-up to cents, as the backend does. */
export function vatAmount(subtotal: number, mode: VatMode, rate: number): number {
  return mode === 'STANDARD' ? Math.round((subtotal * rate + Number.EPSILON) ) / 100 : 0;
}
