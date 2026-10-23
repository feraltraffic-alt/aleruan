import { accessToken } from '../auth';
import type { Api, CheckResult, Counterparty, NewCounterparty } from './types';

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) });

/** Message of a 403: the UI translates it into "you have no access" instead of showing a raw code. */
export const FORBIDDEN = 'FORBIDDEN';

/** Error with the server's problem detail (RFC 7807) message, ready to show to the user. */
export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function authHeaders(): Promise<Record<string, string>> {
  const token = await accessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await accessToken();
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });
  if (res.status === 403) throw new ApiError(403, FORBIDDEN);
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = body.detail ?? body.title ?? detail;
    } catch {
      /* body is not JSON */
    }
    throw new ApiError(res.status, detail);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}

async function problem(res: Response): Promise<string> {
  try {
    const b = await res.json();
    return b.detail ?? b.title ?? res.statusText;
  } catch {
    return res.statusText;
  }
}

async function upload<T>(path: string, file: Blob, fileName: string): Promise<T> {
  const form = new FormData();
  form.append('file', file, fileName);
  const res = await fetch(`/api${path}`, { method: 'POST', body: form, headers: await authHeaders() });
  if (res.status === 403) throw new ApiError(403, FORBIDDEN);
  if (!res.ok) throw new ApiError(res.status, await problem(res));
  return res.json();
}

async function blob(path: string): Promise<Blob> {
  const res = await fetch(`/api${path}`, { headers: await authHeaders() });
  if (res.status === 403) throw new ApiError(403, FORBIDDEN);
  if (!res.ok) throw new ApiError(res.status, res.statusText);
  return res.blob();
}

const qs = (params: Record<string, string | undefined>) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => v && p.set(k, v));
  const s = p.toString();
  return s ? `?${s}` : '';
};

export const httpApi: Api = {
  listCounterparties: (q, role) => request(`/counterparties${qs({ q, role })}`),
  getCounterparty: (id) => request(`/counterparties/${id}`),
  createCounterparty: (input: NewCounterparty) =>
    request<Counterparty>('/counterparties', { method: 'POST', body: JSON.stringify(input) }),
  runChecks: (id) => request(`/counterparties/${id}/checks`, { method: 'POST' }),
  checkHistory: (id) => request(`/counterparties/${id}/checks`),
  checkAccount: (id, account) => request<CheckResult>(`/counterparties/${id}/account-checks`,
    { method: 'POST', body: JSON.stringify({ account }) }),
  lookup: async (country, companyId, vatNumber) => {
    try {
      return await request(`/counterparties/lookup${qs({ country, companyId, vatNumber })}`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null;
      throw e;
    }
  },
  verifyAudit: () => request('/audit/verify'),
  auditHistory: (type, id) => request(`/audit/${type}/${id}`),
  workTypes: () => request('/reference/work-types'),
  setWorkTypes: (id, workTypes) => request(`/counterparties/${id}/work-types`, json('PUT', { workTypes })),

  listDeals: (q, status) => request(`/deals${qs({ q, status })}`),
  getDeal: (id) => request(`/deals/${id}`),
  createDeal: (input) => request('/deals', json('POST', input)),
  editDeal: (id, patch) => request(`/deals/${id}`, json('PUT', patch)),
  addLine: (dealId, line) => request(`/deals/${dealId}/lines`, json('POST', line)),
  updateLine: (dealId, lineId, line) => request(`/deals/${dealId}/lines/${lineId}`, json('PUT', line)),
  removeLine: (dealId, lineId) => request(`/deals/${dealId}/lines/${lineId}`, json('DELETE')),
  moveDeal: (id, status) => request(`/deals/${id}/status`, json('POST', { status })),

  dealRfqs: (dealId) => request(`/deals/${dealId}/rfqs`),
  createRfq: (dealId, input) => request(`/deals/${dealId}/rfqs`, json('POST', input)),
  getRfq: (id) => request(`/rfqs/${id}`),
  rfqCandidates: (workType) => request(`/rfqs/candidates${qs({ workType })}`),
  invite: (rfqId, subcontractorId, allowRisky) => request(`/rfqs/${rfqId}/invitations`, json('POST', { subcontractorId, allowRisky })),
  award: (rfqId, invitationId) => request(`/rfqs/${rfqId}/award`, json('POST', { invitationId })),
  cancelRfq: (rfqId) => request(`/rfqs/${rfqId}/cancel`, json('POST')),

  quoteDefaults: (dealId) => request(`/deals/${dealId}/quotes/defaults`),
  listQuotes: (dealId) => request(`/deals/${dealId}/quotes`),
  issueQuote: (dealId, input) => request(`/deals/${dealId}/quotes`, json('POST', input)),
  quoteFile: async (id) => {
    const res = await fetch(`/api/quotes/${id}/pdf`, { headers: await authHeaders() });
    if (!res.ok) throw new ApiError(res.status, res.statusText);
    return res.blob();
  },

  config: () => request('/config'),
  listSites: (f = {}) => request(`/sites${qs({ ...f })}`),
  getSite: (id) => request(`/sites/${id}`),
  createSite: (dealId) => request(`/deals/${dealId}/site`, json('POST')),
  editSite: (id, details) => request(`/sites/${id}`, json('PUT', details)),
  addTask: (siteId, task) => request(`/sites/${siteId}/tasks`, json('POST', task)),
  updateTask: (siteId, taskId, task) => request(`/sites/${siteId}/tasks/${taskId}`, json('PUT', task)),
  removeTask: (siteId, taskId) => request(`/sites/${siteId}/tasks/${taskId}`, json('DELETE')),
  tasksFromDeal: (siteId) => request(`/sites/${siteId}/tasks/from-deal`, json('POST')),
  workload: (from, to) => request(`/workload${qs({ from, to })}`),

  changeOrders: (dealId) => request(`/deals/${dealId}/change-orders`),
  createChangeOrder: (dealId, draft) => request(`/deals/${dealId}/change-orders`, json('POST', draft)),
  getChangeOrder: (id) => request(`/change-orders/${id}`),
  editChangeOrder: (id, draft) => request(`/change-orders/${id}`, json('PUT', draft)),
  addChangeLine: (id, line) => request(`/change-orders/${id}/lines`, json('POST', line)),
  updateChangeLine: (id, lineId, line) => request(`/change-orders/${id}/lines/${lineId}`, json('PUT', line)),
  removeChangeLine: (id, lineId) => request(`/change-orders/${id}/lines/${lineId}`, json('DELETE')),
  changeOrderAction: (id, action) => request(`/change-orders/${id}/${action}`, json('POST')),
  decideChangeOrder: (id, decision, by, note) => request(`/change-orders/${id}/${decision}`, json('POST', { by, note })),
  changeOrderFile: async (id) => {
    const res = await fetch(`/api/change-orders/${id}/pdf`, { headers: await authHeaders() });
    if (!res.ok) throw new ApiError(res.status, res.statusText);
    return res.blob();
  },

  stages: (siteId) => request(`/sites/${siteId}/stages`),
  createStage: (siteId, data) => request(`/sites/${siteId}/stages`, json('POST', data)),
  editStage: (id, data) => request(`/stages/${id}`, json('PUT', data)),
  removeStage: (id) => request(`/stages/${id}`, json('DELETE')),
  stageAction: (id, action) => request(`/stages/${id}/${action}`, json('POST')),
  decideStage: (id, decision) => request(`/stages/${id}/decision`, json('POST', decision)),
  protocolFile: async (id) => {
    const res = await fetch(`/api/stages/${id}/protocol`, { headers: await authHeaders() });
    if (!res.ok) throw new ApiError(res.status, res.statusText);
    return res.blob();
  },

  setHandover: (siteId, handoverDate, warrantyMonths) =>
    request(`/sites/${siteId}/handover`, json('PUT', { handoverDate, warrantyMonths })),
  diary: (siteId) => request(`/sites/${siteId}/diary`),
  addDiaryEntry: (siteId, data) => request(`/sites/${siteId}/diary`, json('POST', data)),
  editDiaryEntry: (id, data) => request(`/diary/${id}`, json('PUT', data)),
  siteDefects: (siteId) => request(`/sites/${siteId}/defects`),
  unresolvedDefects: () => request('/defects'),
  reportDefect: (siteId, data) => request(`/sites/${siteId}/defects`, json('POST', data)),
  editDefect: (id, data) => request(`/defects/${id}`, json('PUT', data)),
  moveDefect: (id, status, note) => request(`/defects/${id}/status`, json('POST', { status, note })),
  uploadPhoto: async (owner, ownerId, file, fileName) => {
    const form = new FormData();
    form.append('file', file, fileName);
    const res = await fetch(`/api/${owner}/${ownerId}/photos`, { method: 'POST', body: form, headers: await authHeaders() });
    if (!res.ok) {
      let detail = res.statusText;
      try { const b = await res.json(); detail = b.detail ?? b.title ?? detail; } catch { /* not JSON */ }
      throw new ApiError(res.status, detail);
    }
    return res.json();
  },
  removePhoto: (owner, ownerId, fileId) => request(`/${owner}/${ownerId}/photos/${fileId}`, json('DELETE')),
  fileBlob: async (id) => {
    const res = await fetch(`/api/files/${id}`, { headers: await authHeaders() });
    if (!res.ok) throw new ApiError(res.status, res.statusText);
    return res.blob();
  },

  listInvoices: (f = {}) => request(`/invoices${qs({ status: f.status, dealId: f.dealId, customerId: f.customerId,
    overdue: f.overdue ? 'true' : undefined })}`),
  getInvoice: (id) => request(`/invoices/${id}`),
  dealInvoicing: (dealId) => request(`/deals/${dealId}/invoicing`),
  draftInvoice: (dealId, source, stageId) => request(`/deals/${dealId}/invoices`, json('POST', { source, stageId })),
  editInvoice: (id, data) => request(`/invoices/${id}`, json('PUT', data)),
  deleteInvoice: (id) => request(`/invoices/${id}`, json('DELETE')),
  issueInvoice: (id, exchangeRate) => request(`/invoices/${id}/issue`, json('POST', { exchangeRate: exchangeRate ?? null })),
  payInvoice: (id, amount, paidOn) => request(`/invoices/${id}/payments`, json('POST', { amount, paidOn })),
  creditInvoice: (id, reason) => request(`/invoices/${id}/credit`, json('POST', { reason })),
  invoiceFile: async (id) => {
    const res = await fetch(`/api/invoices/${id}/pdf`, { headers: await authHeaders() });
    if (!res.ok) throw new ApiError(res.status, res.statusText);
    return res.blob();
  },

  dealDocuments: (dealId) => request(`/deals/${dealId}/documents`),
  uploadDealDocument: (dealId, file, fileName) => upload(`/deals/${dealId}/documents`, file, fileName),
  removeDealDocument: (dealId, fileId) => request(`/deals/${dealId}/documents/${fileId}`, json('DELETE')),
  dealDocumentBlob: (dealId, fileId) => blob(`/deals/${dealId}/documents/${fileId}`),
  evidence: (dealId) => request(`/deals/${dealId}/evidence`),
  exportEvidence: async (dealId) => {
    const res = await fetch(`/api/deals/${dealId}/evidence/export`, { method: 'POST', headers: await authHeaders() });
    if (res.status === 403) throw new ApiError(403, FORBIDDEN);
    if (!res.ok) throw new ApiError(res.status, await problem(res));
    const disposition = res.headers.get('Content-Disposition') ?? '';
    const name = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1] ?? /filename="?([^";]+)"?/i.exec(disposition)?.[1];
    return { blob: await res.blob(), fileName: name ? decodeURIComponent(name) : `evidence-${dealId}.zip`,
      sha256sums: res.headers.get('X-Evidence-Sha256sums') };
  },

  openOffer: (token) => request(`/public/rfq/${token}`),
  submitOffer: (token, prices, note) => request(`/public/rfq/${token}/offer`, json('POST', { prices, note })),
};
