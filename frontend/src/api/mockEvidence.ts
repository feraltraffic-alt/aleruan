import type {
  Api, AuditRecord, Counterparty, DealDocument, EvidenceCheck, EvidenceItem, EvidenceLevel, EvidenceOverview,
} from './types';
import { ApiError } from './http';
import { localToday } from '../dates';

/** Evidence package for the demo API: the same sections and completeness rules as the backend's EvidenceService. */

export type EvidenceApi = Pick<Api, 'dealDocuments' | 'uploadDealDocument' | 'removeDealDocument' | 'dealDocumentBlob'
  | 'evidence' | 'exportEvidence'>;

const FOLDERS: Record<EvidenceItem['section'], string> = {
  CONTRACT: '01-contract', QUOTES: '02-quotes', CHANGE_ORDERS: '03-change-orders', ACCEPTANCE: '04-acceptance',
  INVOICES: '05-invoices', DIARY: '06-site-diary', DEFECTS: '07-defects', CHECKS: '08-checks', AUDIT: '09-audit',
};
const ORDER = Object.keys(FOLDERS) as EvidenceItem['section'][];
const RUNNING = ['WON', 'IN_PROGRESS', 'COMPLETED'];
const SEVERITY: Record<EvidenceLevel, number> = { MISSING: 0, WARN: 1, OK: 2 };

const LETTERS: Record<string, string> = { ł: 'l', Ł: 'L', ø: 'o', Ø: 'O', đ: 'd', Đ: 'D', ß: 'ss', æ: 'ae', Æ: 'AE',
  œ: 'oe', Œ: 'OE' };

export const safeName = (name: string) => {
  const plain = name.replace(/[łŁøØđĐßæÆœŒ]/g, (c) => LETTERS[c]).normalize('NFD').replace(/\p{M}+/gu, '');
  const safe = plain.replace(/[^A-Za-z0-9._-]+/g, '_').replace(/_{2,}/g, '_').replace(/^[._]+/, '');
  return safe || 'file';
};

const hex = () => Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, '0')).join('');

export function createEvidenceMock(
  api: Omit<Api, keyof EvidenceApi>, counterparties: Counterparty[], audit: AuditRecord[],
  record: (type: string, id: string, action: string, payload: unknown) => void,
  delay: <T>(v: T) => Promise<T>,
): EvidenceApi {
  const docs: (DealDocument & { dealId: string; blob: Blob })[] = [];
  const docsOf = (dealId: string): DealDocument[] =>
    docs.filter((d) => d.dealId === dealId).map(({ dealId: _d, blob: _b, ...d }) => d);

  const overview = async (dealId: string): Promise<EvidenceOverview> => {
    const deal = await api.getDeal(dealId);
    const [quotes, changeOrders, invoices, sites] = await Promise.all([
      api.listQuotes(dealId), api.changeOrders(dealId), api.listInvoices({ dealId }), api.listSites({}),
    ]);
    const items: EvidenceItem[] = [];
    const checks: EvidenceCheck[] = [];
    const add = (i: Partial<EvidenceItem> & Pick<EvidenceItem, 'section' | 'title'>) => items.push({
      date: null, by: null, status: null, amount: null, currency: null, reference: null, text: null, files: [], ...i });
    const check = (code: string, level: EvidenceLevel, params: Record<string, string> = {}) =>
      checks.push({ code, level, params });

    const contract = docsOf(dealId);
    contract.forEach((d) => add({ section: 'CONTRACT', title: d.fileName, date: d.uploadedAt.slice(0, 10),
      by: d.uploadedBy, files: [{ path: `${FOLDERS.CONTRACT}/${safeName(d.fileName)}`, sha256: d.sha256, generated: false }] }));
    if (contract.length) check('CONTRACT_ATTACHED', 'OK', { count: String(contract.length) });
    else if (RUNNING.includes(deal.status)) check('CONTRACT_ATTACHED', 'MISSING');

    [...quotes].sort((a, b) => a.version - b.version).forEach((q) => add({ section: 'QUOTES', title: q.number,
      date: q.issuedOn, by: q.issuedBy, amount: q.total, currency: q.currency, reference: q.number,
      files: [{ path: `${FOLDERS.QUOTES}/${safeName(q.number.replace('/', '-'))}.pdf`, sha256: q.pdfSha256, generated: false }] }));
    if (quotes.length) check('QUOTE_ISSUED', 'OK', { number: quotes[0].number });
    else check('QUOTE_ISSUED', 'MISSING');

    changeOrders.forEach((c) => {
      if (c.status === 'SENT') check('CHANGE_ORDER_UNDECIDED', 'WARN', { number: c.number });
      if (c.status !== 'APPROVED') return;
      add({ section: 'CHANGE_ORDERS', title: `${c.number} ${c.title}`, date: c.decidedAt?.slice(0, 10) ?? null,
        by: c.decidedBy, status: c.status, amount: c.totals.price, currency: deal.currency, reference: c.number,
        files: [{ path: `${FOLDERS.CHANGE_ORDERS}/${c.number}.pdf`, sha256: null, generated: true }] });
    });

    const site = sites.find((s) => s.dealId === dealId);
    if (!site) {
      if (deal.direction === 'CONSTRUCTION' && RUNNING.includes(deal.status)) check('SITE_NOT_OPENED', 'WARN');
    } else {
      const [{ stages }, diary, defects] = await Promise.all([
        api.stages(site.id), api.diary(site.id), api.siteDefects(site.id)]);
      let accepted = 0;
      stages.filter((s) => !['PLANNED', 'SUBMITTED'].includes(s.status)).forEach((s) => {
        if (s.status === 'ACCEPTED' || s.status === 'ACCEPTED_WITH_REMARKS') accepted += s.amount;
        const number = `${deal.number}-P${s.seq}`;
        add({ section: 'ACCEPTANCE', title: `${number} ${s.title}`, date: s.decidedAt?.slice(0, 10) ?? null,
          by: s.decidedBy, status: s.status, amount: s.amount, currency: deal.currency, reference: number, text: s.remarks,
          files: [{ path: `${FOLDERS.ACCEPTANCE}/${number}.pdf`, sha256: null, generated: true }] });
      });
      const value = deal.contract.total.price;
      check('ACCEPTANCE_COVERAGE', value > 0 && accepted >= value ? 'OK' : 'WARN',
        { accepted: String(accepted), contract: String(value), currency: deal.currency });
      [...diary].reverse().forEach((e) => add({ section: 'DIARY', title: e.weather ?? '', date: e.entryDate, by: e.author,
        text: e.text, files: e.photos.map((p) => ({ path: `${FOLDERS.DIARY}/${e.entryDate}-${safeName(p.fileName)}`,
          sha256: hex(), generated: false })) }));
      check('DIARY_KEPT', diary.length ? 'OK' : 'WARN', { count: String(diary.length) });
      const open = defects.filter((d) => d.status === 'OPEN' || d.status === 'FIXED').length;
      [...defects].sort((a, b) => a.seq - b.seq).forEach((d) => add({ section: 'DEFECTS', title: `${d.seq}. ${d.title}`,
        date: d.foundOn, by: d.createdBy, status: d.status,
        text: [d.location, d.description, d.fixNote].filter(Boolean).join('\n') || null,
        files: d.photos.map((p) => ({ path: `${FOLDERS.DEFECTS}/defect-${d.seq}-${safeName(p.fileName)}`, sha256: hex(),
          generated: false })) }));
      check('DEFECTS_OPEN', open ? 'WARN' : 'OK', { count: String(open) });
      if (site.handoverDate) check('HANDOVER', 'OK', { date: site.handoverDate });
      else check('HANDOVER', 'WARN');
    }

    let backed = 0;
    let toCheck = 0;
    invoices.filter((i) => i.status !== 'DRAFT').forEach((i) => {
      add({ section: 'INVOICES', title: i.number ?? '', date: i.issueDate, by: i.issuedBy, status: i.status,
        amount: i.total, currency: i.currency, reference: i.variableSymbol,
        files: [{ path: `${FOLDERS.INVOICES}/${i.number}.pdf`, sha256: i.pdfSha256, generated: false }] });
      if (i.kind !== 'INVOICE' || i.status === 'CREDITED') return;
      toCheck++;
      if (i.lines.some((l) => l.stageId)) backed++;
      else check('INVOICE_WITHOUT_ACCEPTANCE', 'WARN', { number: i.number ?? '' });
      if (i.overdue) check('INVOICE_OVERDUE', 'WARN', { number: i.number ?? '', due: i.dueDate });
    });
    if (toCheck > 0 && backed === toCheck) check('INVOICES_BACKED', 'OK', { count: String(backed) });

    const parties = new Map<string, string>();
    if (deal.customer) parties.set(deal.customer.id, 'CUSTOMER');
    deal.lines.forEach((l) => l.subcontractor && !parties.has(l.subcontractor.id) && parties.set(l.subcontractor.id, 'SUBCONTRACTOR'));
    parties.forEach((role, id) => {
      const c = counterparties.find((x) => x.id === id);
      if (!c) return;
      const params = { name: c.legalName, role };
      if (!c.checkStatus || !c.lastCheckedAt) { check('COUNTERPARTY_UNCHECKED', 'MISSING', params); return; }
      const date = c.lastCheckedAt.slice(0, 10);
      check(c.checkStatus === 'GREEN' ? 'COUNTERPARTY_CHECKED' : 'COUNTERPARTY_FLAGGED',
        c.checkStatus === 'GREEN' ? 'OK' : 'WARN', { ...params, status: c.checkStatus, date });
      add({ section: 'CHECKS', title: `${c.legalName} — ${c.country === 'CZ' ? 'ARES' : 'VIES'}`, date,
        status: c.checkStatus, reference: `demo-${c.id.slice(0, 8)}`,
        files: [{ path: `${FOLDERS.CHECKS}/${safeName(c.legalName)}-checks.json`, sha256: null, generated: true }] });
    });

    check('AUDIT_CHAIN', 'OK', { verified: String(audit.length) });
    add({ section: 'AUDIT', title: 'audit-log.json', files: [{ path: `${FOLDERS.AUDIT}/audit-log.json`, sha256: null, generated: true }] });

    items.sort((a, b) => ORDER.indexOf(a.section) - ORDER.indexOf(b.section));
    checks.sort((a, b) => SEVERITY[a.level] - SEVERITY[b.level]);
    const last = [...audit].reverse().find((r) => r.entityType === 'Deal' && r.entityId === dealId && r.action === 'EVIDENCE_EXPORTED');
    const lastPayload = last ? JSON.parse(last.payload ?? '{}') : null;
    return {
      deal: { id: deal.id, number: deal.number, title: deal.title, customer: deal.customer?.legalName ?? null,
        language: deal.customer && ['CZ', 'SK'].includes(counterparties.find((c) => c.id === deal.customer!.id)?.country ?? '')
          ? 'CS' : 'EN', contractValue: deal.contract.total.price, currency: deal.currency },
      items, checks,
      score: { ok: checks.filter((c) => c.level === 'OK').length, warn: checks.filter((c) => c.level === 'WARN').length,
        missing: checks.filter((c) => c.level === 'MISSING').length },
      lastExport: last ? { at: last.occurredAt, by: last.actor, sha256sums: lastPayload.sha256sums ?? null,
        files: lastPayload.files ?? null } : null,
    };
  };

  return {
    dealDocuments: async (dealId) => { await api.getDeal(dealId); return delay(docsOf(dealId)); },
    uploadDealDocument: async (dealId, file, fileName) => {
      await api.getDeal(dealId);
      if (!/pdf|jpeg|png|webp/.test(file.type) && !/\.(pdf|jpe?g|png|webp)$/i.test(fileName)) {
        throw new ApiError(400, 'Only photos (JPEG, PNG, WebP) and PDF files can be attached');
      }
      if (file.size > 12 * 1024 * 1024) throw new ApiError(400, 'The file is larger than 12 MB');
      const d = { id: crypto.randomUUID(), dealId, fileName, contentType: file.type || 'application/pdf', sizeBytes: file.size,
        sha256: hex(), uploadedAt: new Date().toISOString(), uploadedBy: 'demo', blob: file };
      docs.push(d);
      record('File', d.id, 'UPLOADED', { ownerType: 'Deal', ownerId: dealId, fileName, sha256: d.sha256 });
      const { dealId: _d, blob: _b, ...out } = d;
      return delay(out);
    },
    removeDealDocument: async (dealId, fileId) => {
      const i = docs.findIndex((d) => d.id === fileId && d.dealId === dealId);
      if (i < 0) throw new ApiError(404, `File ${fileId} not found`);
      const [d] = docs.splice(i, 1);
      record('File', fileId, 'DELETED', { ownerType: 'Deal', ownerId: dealId, fileName: d.fileName, sha256: d.sha256 });
      return delay(undefined);
    },
    dealDocumentBlob: async (dealId, fileId) => {
      const d = docs.find((x) => x.id === fileId && x.dealId === dealId);
      if (!d) throw new ApiError(404, `File ${fileId} not found`);
      return d.blob;
    },
    evidence: async (dealId) => delay(await overview(dealId)),
    exportEvidence: async (dealId) => {
      const o = await overview(dealId);
      const files = o.items.flatMap((i) => i.files);
      const sums = hex();
      record('Deal', dealId, 'EVIDENCE_EXPORTED', { sha256sums: sums, files: files.length + 4 });
      const text = [`Demo: the real system builds a ZIP with these files, summary PDFs, manifest.json and SHA256SUMS.`,
        `${o.deal.number} ${o.deal.title}`, '', ...files.map((f) => `${f.sha256 ?? '(rendered at export)'}  ${f.path}`)].join('\n');
      const day = localToday().replace(/-/g, '');
      return { blob: new Blob([text], { type: 'text/plain' }), fileName: `evidence-${o.deal.number}-${day}-demo.txt`,
        sha256sums: sums };
    },
  };
}
