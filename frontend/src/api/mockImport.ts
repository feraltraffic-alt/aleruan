import type {
  Api, Counterparty, DocType, Extraction, ImportDetail, ImportDoc, ImportReview, ImportStatus, ImportWarning, PartyRole,
  ReadParty, SupplierInvoice,
} from './types';
import { ApiError } from './http';
import { addDays, localToday } from '../dates';

/**
 * Document import for the demo API. A stand-in for Claude "reads" an upload a few seconds later; the
 * review and confirmation follow the backend's ImportService rules.
 */

export type ImportApi = Pick<Api, 'importQueue' | 'listImports' | 'uploadImport' | 'getImport' | 'importFile'
  | 'retryImport' | 'rejectImport' | 'confirmImport' | 'listSupplierInvoices' | 'supplierInvoiceFile'
  | 'linkSupplierInvoice' | 'paySupplierInvoice'>;

const US: ReadParty = { name: 'Aleruan-industry s.r.o.', companyId: '14220997', vatNumber: 'CZ14220997',
  address: 'Malešická 2855/2b, 130 00 Praha 3', country: 'CZ', bankAccount: '' };
const OURS = new Set(['14220997', 'CZ14220997']);
const id = (s: string) => s.replace(/[\s.\-/]/g, '').toUpperCase();
const isUs = (p: ReadParty) => OURS.has(id(p.companyId)) || OURS.has(id(p.vatNumber));
const num = (s: string) => {
  if (!s.trim()) return null;
  const v = Number(s.replace(/\s/g, '').replace(',', '.'));
  return Number.isFinite(v) ? v : null;
};
const DEFAULT_ROLE: Record<DocType, PartyRole | null> = {
  SUPPLIER_INVOICE: 'SUBCONTRACTOR', CUSTOMER_INVOICE: 'CUSTOMER', CONTRACT: 'CUSTOMER', CMR: 'CARRIER',
  CREDIT_NOTE: null, OTHER: null,
};

/** A scan-like picture for demo documents, so the review screen has something to show. */
export function demoScan(lines: string[]): Blob {
  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const text = lines.map((l, i) => `<text x="40" y="${70 + i * 30}" font-size="${i === 0 ? 26 : 17}"`
    + ` font-weight="${i === 0 ? 700 : 400}">${esc(l)}</text>`).join('');
  return new Blob([`<svg xmlns="http://www.w3.org/2000/svg" width="620" height="877" font-family="Helvetica, Arial">`
    + `<rect width="620" height="877" fill="#fdfcf8"/><rect x="20" y="20" width="580" height="837" fill="none" stroke="#ddd"/>`
    + `${text}</svg>`], { type: 'image/svg+xml' });
}

function warningsOf(e: Extraction): ImportWarning[] {
  const w: ImportWarning[] = [];
  const net = num(e.netAmount);
  const vat = num(e.vatAmount);
  const total = num(e.totalAmount);
  const invoice = ['SUPPLIER_INVOICE', 'CUSTOMER_INVOICE', 'CREDIT_NOTE'].includes(e.documentType);
  if (invoice && !e.documentNumber) w.push({ code: 'MISSING_NUMBER', params: {} });
  if (invoice && total === null) w.push({ code: 'MISSING_TOTAL', params: {} });
  if (net !== null && vat !== null && total !== null && Math.abs(net + vat - total) > 0.02) {
    w.push({ code: 'AMOUNTS_DONT_ADD_UP', params: { net: String(net), vat: String(vat), total: String(total) } });
  }
  if (e.issueDate && e.dueDate && e.dueDate < e.issueDate) w.push({ code: 'DUE_BEFORE_ISSUE', params: {} });
  if (e.documentType === 'SUPPLIER_INVOICE' && isUs(e.issuer)) w.push({ code: 'WE_ARE_THE_ISSUER', params: {} });
  e.warnings.filter(Boolean).forEach((text) => w.push({ code: 'READER_NOTE', params: { text } }));
  return w;
}

const otherSide = (e: Extraction): ReadParty =>
  isUs(e.issuer) ? e.recipient : isUs(e.recipient) ? e.issuer : e.documentType === 'CUSTOMER_INVOICE' ? e.recipient : e.issuer;

export function createImportMock(api: Pick<Api, 'listDeals' | 'getDeal' | 'uploadDealDocument'>,
  counterparties: Counterparty[], record: (type: string, id: string, action: string, payload: unknown) => void,
  delay: <T>(v: T) => Promise<T>, options: { readAfterMs?: number } = {}): ImportApi {
  type Stored = ImportDoc & { blob: Blob; read: Extraction | null; confirmed: ImportReview | null; warnings: ImportWarning[] };
  const docs: Stored[] = [];
  const invoices: (Omit<SupplierInvoice, 'supplier' | 'dealNumber' | 'overdue'> & { supplierId: string; blob: Blob | null })[] = [];
  const readAfter = options.readAfterMs ?? 2500;
  let seq = 117;

  const view = ({ blob: _b, read: _r, confirmed: _c, warnings: _w, ...d }: Stored): ImportDoc => d;
  const find = (docId: string) => {
    const d = docs.find((x) => x.id === docId);
    if (!d) throw new ApiError(404, `Document ${docId} not found`);
    return d;
  };
  const matchParty = (country: string, companyId: string, vat: string) =>
    counterparties.find((c) => (companyId && c.country === country && c.companyId === id(companyId))
      || (vat && c.vatNumber === id(vat)));
  const invoiceView = async (i: (typeof invoices)[number]): Promise<SupplierInvoice> => {
    const c = counterparties.find((x) => x.id === i.supplierId);
    const deal = i.dealId ? await api.getDeal(i.dealId).catch(() => null) : null;
    const { supplierId: _s, blob: _b, ...rest } = i;
    return { ...rest, supplier: { id: i.supplierId, legalName: c?.legalName ?? i.supplierId, country: c?.country ?? null,
      checkStatus: c?.checkStatus ?? null }, dealNumber: deal?.number ?? null,
    overdue: !i.paidOn && !!i.dueDate && i.dueDate < localToday() };
  };

  /** What the stand-in for Claude "reads" from a file name. */
  const demoRead = (fileName: string): Extraction => {
    const n = fileName.toLowerCase();
    const empty: ReadParty = { name: '', companyId: '', vatNumber: '', address: '', country: '', bankAccount: '' };
    const base = { summary: '', documentNumber: '', issueDate: '', taxableDate: '', dueDate: '', currency: '',
      variableSymbol: '', issuer: empty, recipient: empty, netAmount: '', vatAmount: '', totalAmount: '', vatNote: '',
      references: '', lines: [], warnings: [] };
    if (/smlouv|contract|umow|vertrag/.test(n)) {
      return { ...base, documentType: 'CONTRACT', summary: 'Contract for works (demo reading).', documentNumber: 'SOD-2026-14',
        issueDate: addDays(localToday(), -20), issuer: US, recipient: { ...empty, name: 'Investor Kolín a.s.', country: 'CZ',
          companyId: '25596641', address: 'Pražská 11, Kolín' } };
    }
    if (/cmr/.test(n)) {
      return { ...base, documentType: 'CMR', summary: 'CMR consignment note (demo reading).', documentNumber: 'CMR 0447812',
        issueDate: addDays(localToday(), -3), issuer: { ...empty, name: 'Transkom Logistyka Sp. z o.o.', country: 'PL',
          companyId: '6330434839', vatNumber: 'PL6330434839' }, recipient: US };
    }
    seq += 1;
    const net = 18400 + seq;
    const vat = 0; // reverse charge (§ 92e): the customer pays the VAT
    return { ...base, documentType: 'SUPPLIER_INVOICE', summary: 'Invoice for electrical installation works (demo reading).',
      documentNumber: `2026-0${seq}`, issueDate: addDays(localToday(), -6), taxableDate: addDays(localToday(), -6),
      dueDate: addDays(localToday(), 8), currency: 'CZK', variableSymbol: `20260${seq}`,
      issuer: { name: 'Elektro Brno a.s.', companyId: '17603137', vatNumber: 'CZ17603137', address: 'Křenová 21, 602 00 Brno',
        country: 'CZ', bankAccount: 'CZ65 0800 0000 1920 0014 5399' }, recipient: US,
      netAmount: net.toFixed(2), vatAmount: vat.toFixed(2), totalAmount: (net + vat).toFixed(2),
      vatNote: 'Daň odvede zákazník (§ 92e zákona o DPH)', references: 'D-2026-0002',
      lines: [{ description: 'Elektroinstalace haly, etapa 1', quantity: '1', unit: 'kpl', unitPrice: net.toFixed(2),
        total: net.toFixed(2), vatRate: '21' }],
      warnings: ['The stamp partly covers the bank account number.'] };
  };

  const startReading = (d: Stored) => {
    setTimeout(() => {
      if (d.status !== 'QUEUED') return;
      d.status = 'PROCESSING';
      d.attempts += 1;
      setTimeout(() => {
        if (d.status !== 'PROCESSING') return;
        const e = demoRead(d.fileName);
        Object.assign(d, { status: 'EXTRACTED', read: e, warnings: warningsOf(e), docType: e.documentType,
          summary: e.summary || null, documentNumber: e.documentNumber || null,
          partyName: otherSide(e).name || null, totalAmount: e.totalAmount || null, currency: e.currency || null,
          model: 'demo', inputTokens: 1800, outputTokens: 420, processedAt: new Date().toISOString() });
        record('ImportDocument', d.id, 'READ', { type: e.documentType, model: 'demo' });
      }, readAfter / 2);
    }, readAfter / 2);
  };

  const add = (fileName: string, blob: Blob, contentType: string): Stored => {
    const d: Stored = { id: crypto.randomUUID(), fileName, contentType, status: 'QUEUED', docType: null, summary: null,
      documentNumber: null, partyName: null, totalAmount: null, currency: null, error: null, model: null,
      inputTokens: null, outputTokens: null, attempts: 0, dealId: null, resultType: null, resultId: null,
      uploadedAt: new Date().toISOString(), uploadedBy: 'demo', processedAt: null, decidedAt: null, decidedBy: null,
      blob, read: null, confirmed: null, warnings: [] };
    docs.unshift(d);
    record('ImportDocument', d.id, 'UPLOADED', { fileName });
    return d;
  };

  // Seed: one invoice waiting for review, one received invoice already registered.
  const seeded = add('faktura-elektro-brno.pdf', demoScan(['FAKTURA – daňový doklad 2026-0118', 'Elektro Brno a.s.',
    'IČO 17603137, DIČ CZ17603137', 'Odběratel: Aleruan-industry s.r.o.', 'Elektroinstalace haly, etapa 1',
    'Základ 18 518,00 CZK', 'DPH 0,00 – daň odvede zákazník', 'Celkem 18 518,00 CZK']), 'image/svg+xml');
  seeded.uploadedAt = new Date(Date.now() - 3_600_000).toISOString();
  seeded.status = 'PROCESSING';
  startReading(Object.assign(seeded, { status: 'QUEUED' as ImportStatus }));
  const horak = counterparties.find((c) => c.legalName.startsWith('Stavby Horák'));
  void api.listDeals().then((list) => {
    const gliwice = list.find((d) => d.title.startsWith('Hala'));
    if (!horak || !gliwice) return;
    invoices.push({ id: crypto.randomUUID(), supplierId: horak.id, dealId: gliwice.id, number: 'FV2026/0311',
      variableSymbol: '20260311', issueDate: addDays(localToday(), -25), taxableDate: addDays(localToday(), -25),
      dueDate: addDays(localToday(), -11), currency: 'EUR', netAmount: 9800, vatAmount: 0, total: 9800,
      vatNote: 'Reverse charge', bankAccount: 'CZ12 0100 0000 1234 5678 9012', accountCheck: 'GREEN', hasFile: false,
      fileSha256: null, importId: null, paidOn: null, createdAt: new Date().toISOString(), createdBy: 'demo', blob: null });
  });

  return {
    importQueue: async () => delay({ readerConfigured: true, model: 'demo', queued: docs.filter((d) => d.status === 'QUEUED').length,
      processing: docs.filter((d) => d.status === 'PROCESSING').length, failed: docs.filter((d) => d.status === 'FAILED').length,
      toReview: docs.filter((d) => d.status === 'EXTRACTED').length }),
    listImports: async (status) => delay(docs.filter((d) => !status || d.status === status).map(view)),
    uploadImport: async (file, fileName) => {
      const type = file.type || (/\.pdf$/i.test(fileName) ? 'application/pdf' : '');
      if (!/pdf|jpeg|png|webp/.test(type)) throw new ApiError(400, 'Only photos (JPEG, PNG, WebP) and PDF files can be attached');
      if (file.size > 12 * 1024 * 1024) throw new ApiError(400, 'The file is larger than 12 MB');
      const same = docs.find((d) => d.fileName === fileName && d.blob.size === file.size && d.status !== 'REJECTED');
      if (same) return delay({ document: view(same), duplicate: true });
      const d = add(fileName, file, type);
      startReading(d);
      return delay({ document: view(d), duplicate: false });
    },
    getImport: async (docId) => {
      const d = find(docId);
      const e = d.read;
      const party = e ? otherSide(e) : { name: '', companyId: '', vatNumber: '', address: '', country: '', bankAccount: '' };
      const match = matchParty(party.country, party.companyId, party.vatNumber);
      const duplicate = !!match && !!e && invoices.some((i) => i.supplierId === match.id
        && i.number.toLowerCase() === e.documentNumber.toLowerCase());
      const detail: ImportDetail = { document: view(d), read: e, confirmed: d.confirmed, warnings: d.warnings,
        suggestion: { type: e?.documentType ?? 'OTHER', party, role: DEFAULT_ROLE[e?.documentType ?? 'OTHER'],
          match: match ? { id: match.id, legalName: match.legalName, country: match.country, checkStatus: match.checkStatus,
            companyId: match.companyId, vatNumber: match.vatNumber } : null, duplicateInvoice: duplicate } };
      return delay(detail);
    },
    importFile: async (docId) => find(docId).blob,
    retryImport: async (docId) => {
      const d = find(docId);
      if (['CONFIRMED', 'REJECTED', 'PROCESSING'].includes(d.status)) throw new ApiError(409, `The document is ${d.status}`);
      Object.assign(d, { status: 'QUEUED', error: null, attempts: 0 });
      startReading(d);
      return delay(view(d));
    },
    rejectImport: async (docId, reason) => {
      const d = find(docId);
      if (['CONFIRMED', 'REJECTED'].includes(d.status)) throw new ApiError(409, `The document is already ${d.status}`);
      Object.assign(d, { status: 'REJECTED', error: reason, decidedAt: new Date().toISOString(), decidedBy: 'demo' });
      record('ImportDocument', d.id, 'REJECTED', { reason });
      return delay(view(d));
    },
    confirmImport: async (docId, r) => {
      const d = find(docId);
      if (!['QUEUED', 'EXTRACTED', 'FAILED'].includes(d.status)) throw new ApiError(409, `The document is ${d.status}`);
      const invoiceType = ['SUPPLIER_INVOICE', 'CUSTOMER_INVOICE', 'CREDIT_NOTE'].includes(r.type);
      const hasParty = !!r.counterpartyId || !!r.party?.name.trim();
      if (!hasParty && invoiceType) throw new ApiError(400, 'An invoice needs its counterparty');
      const role = hasParty ? (r.role ?? DEFAULT_ROLE[r.type]) : null;
      if (hasParty && !role) throw new ApiError(400, 'Say whether the counterparty is a customer, subcontractor, supplier or carrier');
      let partyId: string | null = null;
      let created = false;
      if (role) {
        let c = r.counterpartyId ? counterparties.find((x) => x.id === r.counterpartyId) : undefined;
        if (!c && r.party) {
          if (!r.party.country) throw new ApiError(400, 'The counterparty needs a name and a country');
          c = matchParty(r.party.country, r.party.companyId ?? '', r.party.vatNumber ?? '');
          if (!c) {
            c = { id: crypto.randomUUID(), legalName: r.party.name.trim(), country: r.party.country.toUpperCase(),
              companyId: r.party.companyId ? id(r.party.companyId) : null, vatNumber: r.party.vatNumber ? id(r.party.vatNumber) : null,
              address: r.party.address, email: null, phone: null, roles: [role], workTypes: [], checkStatus: 'GREEN',
              lastCheckedAt: new Date().toISOString(), createdAt: new Date().toISOString() };
            counterparties.push(c);
            record('Counterparty', c.id, 'CREATED', { legalName: c.legalName });
            created = true;
          }
        }
        if (!c) throw new ApiError(400, 'Counterparty not found');
        if (!c.roles.includes(role)) c.roles.push(role);
        partyId = c.id;
      }
      let resultType = 'ARCHIVED';
      let resultId: string | null = null;
      if (r.type === 'SUPPLIER_INVOICE' || (r.type === 'CREDIT_NOTE' && role && role !== 'CUSTOMER')) {
        if (!r.documentNumber || r.totalAmount == null || !r.currency) {
          throw new ApiError(400, 'A received invoice needs its number, total and currency');
        }
        if (invoices.some((i) => i.supplierId === partyId && i.number.toLowerCase() === r.documentNumber!.toLowerCase())) {
          throw new ApiError(409, `Invoice ${r.documentNumber} of this supplier is already registered`);
        }
        resultId = crypto.randomUUID();
        invoices.unshift({ id: resultId, supplierId: partyId!, dealId: r.dealId, number: r.documentNumber,
          variableSymbol: r.variableSymbol, issueDate: r.issueDate, taxableDate: r.taxableDate, dueDate: r.dueDate,
          currency: r.currency.toUpperCase(), netAmount: r.netAmount, vatAmount: r.vatAmount, total: r.totalAmount,
          vatNote: r.vatNote, bankAccount: r.party?.bankAccount ?? null, accountCheck: r.party?.bankAccount ? 'GREEN' : null,
          hasFile: true, fileSha256: '9f2c'.padEnd(64, '0'), importId: d.id, paidOn: null,
          createdAt: new Date().toISOString(), createdBy: 'demo', blob: d.blob });
        resultType = 'SUPPLIER_INVOICE';
        record('SupplierInvoice', resultId, 'REGISTERED', { number: r.documentNumber });
      } else if (r.type === 'CONTRACT' && r.dealId) {
        resultId = (await api.uploadDealDocument(r.dealId, d.blob, d.fileName)).id;
        resultType = 'DEAL_DOCUMENT';
      } else if (partyId) {
        resultType = 'COUNTERPARTY';
        resultId = partyId;
      }
      Object.assign(d, { status: 'CONFIRMED', confirmed: r, docType: r.type, dealId: r.dealId, resultType, resultId,
        decidedAt: new Date().toISOString(), decidedBy: 'demo' });
      record('ImportDocument', d.id, 'CONFIRMED', { type: r.type, result: resultType });
      return delay({ resultType, resultId, counterpartyId: partyId, counterpartyCreated: created });
    },
    listSupplierInvoices: async (f = {}) => {
      const list = invoices.filter((i) => (!f.dealId || i.dealId === f.dealId) && (!f.supplierId || i.supplierId === f.supplierId));
      return delay(await Promise.all(list.map(invoiceView)));
    },
    supplierInvoiceFile: async (invId) => {
      const i = invoices.find((x) => x.id === invId);
      if (!i?.blob) throw new ApiError(404, 'No file');
      return i.blob;
    },
    linkSupplierInvoice: async (invId, dealId) => {
      const i = invoices.find((x) => x.id === invId);
      if (!i) throw new ApiError(404, `Supplier invoice ${invId} not found`);
      i.dealId = dealId;
      return delay(await invoiceView(i));
    },
    paySupplierInvoice: async (invId, paidOn) => {
      const i = invoices.find((x) => x.id === invId);
      if (!i) throw new ApiError(404, `Supplier invoice ${invId} not found`);
      if (paidOn && paidOn > localToday()) throw new ApiError(400, 'A payment cannot be in the future');
      i.paidOn = paidOn;
      record('SupplierInvoice', invId, paidOn ? 'PAID' : 'PAYMENT_CLEARED', { paidOn });
      return delay(await invoiceView(i));
    },
  };
}
