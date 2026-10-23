import type {
  AcceptedStage, Api, Counterparty, DealStatus, DocLanguage, Invoice, InvoiceEdit, InvoiceLine, VatMode,
} from './types';
import { ApiError } from './http';
import { addDays, localToday } from '../dates';
import { languageFor, suggestVat, vatAmount } from '../vat';

/** Invoices for the demo API; mirrors the backend's InvoiceService rules. */

export type InvoicesApi = Pick<Api, 'listInvoices' | 'getInvoice' | 'dealInvoicing' | 'draftInvoice' | 'editInvoice'
  | 'deleteInvoice' | 'issueInvoice' | 'payInvoice' | 'creditInvoice' | 'invoiceFile'>;

export interface InvoiceAccess {
  deal(id: string): { id: string; number: string; title: string; currency: string; status: DealStatus; customerId: string;
    siteCountry: string | null; direction: string } | undefined;
  contractValue(id: string): number;
  customerLines(id: string): { description: string; unit: string | null; quantity: number; unitPrice: number }[];
  acceptedStages(dealId: string): AcceptedStage[];
}

const round2 = (v: number) => Math.round((v + Number.EPSILON) * 100) / 100;
const DEMO_RATES: Record<string, number> = { EUR: 24.32, PLN: 5.61 };

export function createInvoicesMock(
  counterparties: Counterparty[], access: InvoiceAccess,
  record: (type: string, id: string, action: string, payload: unknown) => void,
  delay: <T>(v: T) => Promise<T>,
): InvoicesApi & { seedIssued(dealId: string, lines: InvoiceLine[], daysAgo: number, paid: boolean): void } {
  const invoices: (Omit<Invoice, 'dealNumber' | 'dealTitle' | 'customer' | 'overdue'> & { customerId: string })[] = [];
  const series = new Map<string, number>();
  const today = localToday;

  const find = (id: string) => {
    const i = invoices.find((x) => x.id === id);
    if (!i) throw new ApiError(404, `Invoice ${id} not found`);
    return i;
  };
  const view = (i: (typeof invoices)[number]): Invoice => {
    const deal = access.deal(i.dealId);
    const c = counterparties.find((x) => x.id === i.customerId);
    const { customerId: _c, ...rest } = i;
    return { ...rest, dealNumber: deal?.number ?? null, dealTitle: deal?.title ?? null,
      customer: c ? { id: c.id, legalName: c.legalName, country: c.country, checkStatus: c.checkStatus } : null,
      overdue: i.status === 'ISSUED' && i.kind === 'INVOICE' && i.dueDate < today() };
  };
  const recompute = (i: (typeof invoices)[number]) => {
    i.lines = i.lines.map((l, n) => ({ ...l, position: n + 1, id: l.id ?? crypto.randomUUID(), total: round2(l.quantity * l.unitPrice) }));
    i.subtotal = round2(i.lines.reduce((s, l) => s + (l.total ?? 0), 0));
    i.vatAmount = vatAmount(i.subtotal, i.vatMode, i.vatRate);
    i.total = round2(i.subtotal + i.vatAmount);
  };
  const taken = (dealId: string, except?: string) => invoices
    .filter((i) => i.dealId === dealId && i.kind === 'INVOICE' && i.status !== 'CREDITED' && i.id !== except)
    .flatMap((i) => i.lines.map((l) => l.stageId)).filter(Boolean);
  const freeze = (i: (typeof invoices)[number], rate: number | null) => {
    const year = Number(today().slice(0, 4));
    const key = `${i.kind}-${year}`;
    const seq = (series.get(key) ?? 0) + 1;
    series.set(key, seq);
    const pad = String(seq).padStart(4, '0');
    Object.assign(i, {
      status: 'ISSUED', number: `${i.kind === 'INVOICE' ? 'FV' : 'OD'}-${year}-${pad}`,
      variableSymbol: `${i.kind === 'INVOICE' ? '' : '9'}${year}${pad}`, issueDate: today(), issuedAt: new Date().toISOString(),
      issuedBy: 'demo', exchangeRate: rate, rateDate: rate ? i.taxableDate : null,
      vatAmountCzk: rate ? round2(i.vatAmount * rate) : null,
      pdfSha256: Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, '0')).join(''),
    });
    record('Invoice', i.id, 'ISSUED', { number: i.number, total: i.total });
  };
  const draft = (dealId: string, lines: InvoiceLine[], taxable: string) => {
    const deal = access.deal(dealId);
    if (!deal) throw new ApiError(404, `Deal ${dealId} not found`);
    if (!['WON', 'IN_PROGRESS', 'COMPLETED'].includes(deal.status)) {
      throw new ApiError(409, `Deal ${deal.number} can be invoiced once the customer has accepted it`);
    }
    const c = counterparties.find((x) => x.id === deal.customerId)!;
    const vat = suggestVat(c.country, c.vatNumber, deal.siteCountry, deal.direction === 'CONSTRUCTION');
    const i: (typeof invoices)[number] = {
      id: crypto.randomUUID(), dealId, customerId: deal.customerId, kind: 'INVOICE', status: 'DRAFT',
      number: null, variableSymbol: null, creditOfId: null, language: languageFor(c.country) as DocLanguage,
      currency: deal.currency, vatMode: vat.mode, vatRate: vat.rate, issueDate: null, taxableDate: taxable,
      dueDate: addDays(today(), 14), subtotal: 0, vatAmount: 0, total: 0, exchangeRate: null, rateDate: null,
      vatAmountCzk: null, note: null, paidAmount: 0, paidOn: null, pdfSha256: null, createdAt: new Date().toISOString(),
      createdBy: 'demo', issuedAt: null, issuedBy: null, lines,
    };
    recompute(i);
    invoices.push(i);
    return i;
  };

  return {
    seedIssued(dealId, lines, daysAgo, paid) {
      const i = draft(dealId, lines, addDays(today(), -daysAgo));
      i.dueDate = addDays(today(), 14 - daysAgo);
      freeze(i, i.currency === 'CZK' ? null : DEMO_RATES[i.currency] ?? null);
      i.issueDate = addDays(today(), -daysAgo);
      if (paid) Object.assign(i, { paidAmount: i.total, paidOn: addDays(today(), -Math.max(0, daysAgo - 10)), status: 'PAID' });
    },
    listInvoices: async (f = {}) => delay(invoices
      .filter((i) => (!f.status || i.status === f.status) && (!f.dealId || i.dealId === f.dealId)
        && (!f.customerId || i.customerId === f.customerId))
      .map(view).filter((i) => !f.overdue || i.overdue)
      .sort((a, b) => (b.issueDate ?? b.taxableDate).localeCompare(a.issueDate ?? a.taxableDate)
        || (a.number === null ? -1 : b.number === null ? 1 : b.number.localeCompare(a.number)))),
    getInvoice: async (id) => delay(view(find(id))),
    dealInvoicing: async (dealId) => {
      const deal = access.deal(dealId);
      if (!deal) throw new ApiError(404, 'Deal not found');
      const issued = invoices.filter((i) => i.dealId === dealId && i.status !== 'DRAFT');
      const invoiced = round2(issued.reduce((s, i) => s + i.subtotal, 0));
      const used = taken(dealId);
      return delay({
        contractValue: access.contractValue(dealId), invoiced,
        paid: round2(issued.filter((i) => i.kind === 'INVOICE').reduce((s, i) => s + i.paidAmount, 0)),
        outstanding: round2(issued.filter((i) => i.status === 'ISSUED' && i.kind === 'INVOICE').reduce((s, i) => s + i.total - i.paidAmount, 0)),
        remaining: round2(access.contractValue(dealId) - invoiced), currency: deal.currency,
        stages: access.acceptedStages(dealId).map((stage) => ({ stage, invoiced: used.includes(stage.id) })),
      });
    },
    draftInvoice: async (dealId, source, stageId) => {
      let lines: InvoiceLine[] = [];
      let taxable = today();
      if (source === 'STAGE') {
        const stage = access.acceptedStages(dealId).find((s) => s.id === stageId);
        if (!stage) throw new ApiError(400, 'Only an accepted stage can be invoiced');
        if (taken(dealId).includes(stage.id)) throw new ApiError(409, `Stage ${stage.seq} is already on an invoice`);
        lines = [{ description: `Etapa ${stage.seq}: ${stage.title}`, unit: null, quantity: 1, unitPrice: stage.amount, stageId: stage.id }];
        if (stage.acceptedAt) taxable = stage.acceptedAt.slice(0, 10);
      }
      if (source === 'DEAL_LINES') lines = access.customerLines(dealId).map((l) => ({ ...l, stageId: null }));
      const i = draft(dealId, lines, taxable);
      record('Invoice', i.id, 'DRAFTED', { source });
      return delay(view(i));
    },
    editInvoice: async (id, data: InvoiceEdit) => {
      const i = find(id);
      if (i.status !== 'DRAFT') throw new ApiError(409, `Invoice ${i.number} is issued; correct it with a credit note`);
      if (!(data.vatRate >= 0 && data.vatRate < 100)) throw new ApiError(400, 'VAT rate must be between 0 and 100');
      if (data.dueDate < data.taxableDate) throw new ApiError(400, 'The due date cannot be before the date of taxable supply');
      if (data.lines.some((l) => !l.description.trim() || !l.quantity)) throw new ApiError(400, 'Every line needs a description and a quantity');
      const others = taken(i.dealId, i.id);
      if (data.lines.some((l) => l.stageId && others.includes(l.stageId))) throw new ApiError(400, 'A stage is already invoiced');
      Object.assign(i, { ...data, vatRate: data.vatMode === 'STANDARD' ? data.vatRate : 0, note: data.note?.trim() || null,
        lines: data.lines.map((l) => ({ ...l, description: l.description.trim() })) });
      recompute(i);
      record('Invoice', id, 'EDITED', {});
      return delay(view(i));
    },
    deleteInvoice: async (id) => {
      const i = find(id);
      if (i.status !== 'DRAFT') throw new ApiError(409, 'Issued invoices are never deleted; issue a credit note');
      invoices.splice(invoices.indexOf(i), 1);
      return delay(undefined);
    },
    issueInvoice: async (id, manual) => {
      const i = find(id);
      if (i.status !== 'DRAFT') throw new ApiError(409, `Invoice ${i.number} is already issued`);
      if (!i.lines.length) throw new ApiError(409, 'An invoice needs at least one line');
      if (i.total <= 0) throw new ApiError(409, 'An invoice total must be positive; use a credit note to reduce');
      const rate = i.currency === 'CZK' ? null : manual ?? DEMO_RATES[i.currency] ?? null;
      if (i.currency !== 'CZK' && rate == null) {
        throw new ApiError(409, `The ČNB rate for ${i.currency} is not available; enter the rate by hand`);
      }
      freeze(i, rate);
      return delay(view(i));
    },
    payInvoice: async (id, amount, paidOn) => {
      const i = find(id);
      if (i.status !== 'ISSUED' || i.kind !== 'INVOICE') throw new ApiError(409, 'Payments are recorded on issued, unpaid invoices');
      if (!(amount > 0)) throw new ApiError(400, 'Payment amount must be positive');
      if (!paidOn || paidOn > today()) throw new ApiError(400, 'A payment is recorded once it has arrived');
      i.paidAmount = round2(i.paidAmount + amount);
      i.paidOn = paidOn;
      if (i.paidAmount >= i.total) i.status = 'PAID';
      record('Invoice', id, 'PAYMENT', { amount, paidOn });
      return delay(view(i));
    },
    creditInvoice: async (id, reason) => {
      const o = find(id);
      if (o.kind !== 'INVOICE' || !(o.status === 'ISSUED' || o.status === 'PAID')) {
        throw new ApiError(409, 'Only an issued invoice can be credited');
      }
      if (!reason.trim()) throw new ApiError(400, 'Say why the invoice is corrected');
      const c: (typeof invoices)[number] = { ...o, id: crypto.randomUUID(), kind: 'CREDIT_NOTE', status: 'DRAFT', creditOfId: o.id,
        number: null, taxableDate: today(), dueDate: today(), note: reason.trim(), paidAmount: 0, paidOn: null,
        lines: o.lines.map((l) => ({ ...l, id: undefined, quantity: -l.quantity })) as InvoiceLine[] };
      recompute(c);
      invoices.push(c);
      freeze(c, o.exchangeRate);
      o.status = 'CREDITED';
      return delay(view(c));
    },
    invoiceFile: async (id) => {
      const i = view(find(id));
      if (i.status === 'DRAFT') throw new ApiError(409, 'A draft has no PDF yet; issue it first');
      const vat = (m: VatMode) => (m === 'STANDARD' ? `DPH ${i.vatRate} %: ${i.vatAmount.toFixed(2)}` : m);
      return new Blob([`<!doctype html><meta charset="utf-8"><title>${i.number}</title><body style="font-family:sans-serif;padding:32px">`
        + `<p>Demo: the real system stores the issued PDF here.</p><h1>${i.number}</h1><p>${i.customer?.legalName ?? ''}</p><ul>`
        + i.lines.map((l) => `<li>${l.description}: ${l.quantity} × ${l.unitPrice.toFixed(2)}</li>`).join('')
        + `</ul><p>${vat(i.vatMode)}</p><p><b>${i.total.toFixed(2)} ${i.currency}</b></p>`], { type: 'text/html' });
    },
  };
}
