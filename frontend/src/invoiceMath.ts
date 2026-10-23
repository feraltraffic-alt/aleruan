import type { VatMode } from './api';

/** Mirror of the backend's InvoiceMath for live totals while editing a draft. */
const round2 = (v: number) => Math.round((v + Number.EPSILON) * 100) / 100;

export const lineTotal = (quantity: number, unitPrice: number) => round2(quantity * unitPrice);

export function invoiceAmounts(lines: { quantity: number; unitPrice: number }[], mode: VatMode, rate: number) {
  const subtotal = round2(lines.reduce((s, l) => s + lineTotal(l.quantity, l.unitPrice), 0));
  const vatAmount = mode === 'STANDARD' ? Math.round(subtotal * rate + Number.EPSILON) / 100 : 0;
  return { subtotal, vatAmount, total: round2(subtotal + vatAmount) };
}
