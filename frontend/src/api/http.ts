import { accessToken } from '../auth';
import type { Api, CheckResult, Counterparty, NewCounterparty } from './types';

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) });

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

  openOffer: (token) => request(`/public/rfq/${token}`),
  submitOffer: (token, prices, note) => request(`/public/rfq/${token}/offer`, json('POST', { prices, note })),
};
