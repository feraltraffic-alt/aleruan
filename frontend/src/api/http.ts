import { accessToken } from '../auth';
import type { Api, CheckResult, Counterparty, NewCounterparty } from './types';

/** Error with the server's problem detail (RFC 7807) message, ready to show to the user. */
export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
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
};
