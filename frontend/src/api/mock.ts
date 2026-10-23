import type {
  Api, AuditRecord, CheckFinding, CheckResult, CheckRun, CheckStatus, Counterparty, NewCounterparty, WorkType,
} from './types';
import { ApiError } from './http';
import { createDealsMock } from './mockDeals';
import { createEvidenceMock, type EvidenceApi } from './mockEvidence';
import { createAnalyticsMock, type AnalyticsApi } from './mockAnalytics';
import { createImportMock, type ImportApi } from './mockImport';

/**
 * In-memory API for demos and tests (VITE_USE_MOCKS=true).
 * Companies are fictional; identifiers have valid checksums so the forms behave like production.
 */

const now = () => new Date().toISOString();
const uid = () => crypto.randomUUID();

function seed(): Counterparty[] {
  const base = (p: Partial<Counterparty> & Pick<Counterparty, 'legalName' | 'country' | 'roles'>): Counterparty => ({
    id: uid(), companyId: null, vatNumber: null, address: null, email: null, phone: null,
    checkStatus: null, lastCheckedAt: null, createdAt: '2026-10-01T08:00:00Z', workTypes: [], ...p,
  });
  return [
    base({ legalName: 'Stavby Horák s.r.o.', country: 'CZ', companyId: '68599528', vatNumber: 'CZ68599528',
      address: 'Vinohradská 112, 130 00 Praha 3', roles: ['SUBCONTRACTOR'],
      workTypes: ['CONSTRUCTION', 'DISMANTLING', 'INTERIOR', 'FINISHING'], checkStatus: 'GREEN', lastCheckedAt: '2026-10-22T07:12:00Z' }),
    base({ legalName: 'Elektro Brno a.s.', country: 'CZ', companyId: '17603137', vatNumber: 'CZ17603137',
      address: 'Křenová 21, 602 00 Brno', roles: ['SUBCONTRACTOR', 'SUPPLIER'],
      workTypes: ['ELECTRICAL', 'INSTALLATION'], checkStatus: 'YELLOW', lastCheckedAt: '2026-10-21T15:40:00Z' }),
    base({ legalName: 'Południe Budowa Sp. z o.o.', country: 'PL', companyId: '2815901396', vatNumber: 'PL2815901396',
      address: 'ul. Długa 5, 31-147 Kraków', roles: ['CUSTOMER'], checkStatus: 'GREEN',
      lastCheckedAt: '2026-10-20T09:05:00Z' }),
    base({ legalName: 'Transkom Logistyka Sp. z o.o.', country: 'PL', companyId: '6330434839',
      vatNumber: 'PL6330434839', address: 'ul. Przemysłowa 8, 05-500 Piaseczno', roles: ['CARRIER'] }),
    base({ legalName: 'Montage Rhein GmbH', country: 'DE', vatNumber: 'DE811234567',
      address: 'Industriestraße 4, 46485 Wesel', roles: ['CUSTOMER'], checkStatus: 'RED',
      lastCheckedAt: '2026-10-18T11:30:00Z' }),
    base({ legalName: 'Softline Dev S.R.L.', country: 'MD', companyId: '1009600012345',
      address: 'str. Ștefan cel Mare 1, Chișinău', roles: ['SUBCONTRACTOR'], workTypes: ['INTERIOR', 'FINISHING'], checkStatus: 'YELLOW',
      lastCheckedAt: '2026-10-15T10:00:00Z' }),
  ];
}

const workTypes: WorkType[] = [
  ['CONSTRUCTION', 'Stavební práce', 'Roboty budowlane', 'Construction works', 'Строительные работы'],
  ['DISMANTLING', 'Demontážní práce', 'Prace demontażowe', 'Dismantling works', 'Демонтажные работы'],
  ['ELECTRICAL', 'Elektroinstalační práce', 'Roboty elektryczne', 'Electrical works', 'Электромонтажные работы'],
  ['FINISHING', 'Dokončovací práce', 'Prace wykończeniowe', 'Finishing works', 'Отделочные работы'],
  ['INSTALLATION', 'Montážní práce', 'Prace instalacyjne', 'Installation works', 'Монтажные работы'],
  ['INTERIOR', 'Vnitřní práce', 'Prace wewnętrzne', 'Interior works', 'Внутренние работы'],
].map(([code, nameCs, namePl, nameEn, nameRu]) => ({
  id: uid(), code, nameCs, namePl, nameEn, nameRu, defaultMarkupPct: null, active: true,
}));

const SEVERITY: Record<CheckStatus, number> = { GREEN: 0, YELLOW: 1, RED: 2 };
const RED_FINDINGS: CheckFinding[] = ['VAT_INVALID', 'NOT_FOUND_IN_REGISTRY', 'UNRELIABLE_VAT_PAYER',
  'VAT_NOT_ACTIVE', 'INSOLVENCY', 'LIQUIDATION_OR_CEASED', 'SANCTIONS_HIT'];
const statusOf = (findings: CheckFinding[]): CheckStatus =>
  findings.some((f) => RED_FINDINGS.includes(f)) ? 'RED' : findings.length ? 'YELLOW' : 'GREEN';

export function createMockApi(): Api {
  const counterparties = seed();
  const checks = new Map<string, CheckResult[]>();
  const audit: AuditRecord[] = [];

  const record = (entityType: string, entityId: string, action: string, payload: unknown) => {
    const prev = audit.at(-1);
    audit.push({
      seq: audit.length + 1, occurredAt: now(), actor: 'demo', entityType, entityId, action,
      payload: JSON.stringify(payload), prevHash: prev?.hash ?? '0'.repeat(64),
      hash: Math.random().toString(16).slice(2).padEnd(64, '0'),
    });
  };

  const find = (id: string) => {
    const c = counterparties.find((x) => x.id === id);
    if (!c) throw new ApiError(404, `Counterparty ${id} not found`);
    return c;
  };

  const delay = <T,>(v: T) => new Promise<T>((r) => setTimeout(() => r(structuredClone(v)), 150));

  const base: Omit<Api, keyof EvidenceApi | keyof AnalyticsApi | keyof ImportApi> = {
    ...createDealsMock(counterparties, record, delay),
    listCounterparties: (q, role) => {
      const needle = q?.toLowerCase().trim();
      return delay(counterparties
        .filter((c) => !role || c.roles.includes(role))
        .filter((c) => !needle || c.legalName.toLowerCase().includes(needle)
          || c.companyId?.includes(needle) || c.vatNumber?.toLowerCase().includes(needle))
        .sort((a, b) => a.legalName.localeCompare(b.legalName)));
    },
    getCounterparty: async (id) => delay(find(id)),
    createCounterparty: async (input: NewCounterparty) => {
      const country = input.country.toUpperCase();
      const companyId = input.companyId?.replace(/[\s.\-/]/g, '') || null;
      if (companyId && counterparties.some((c) => c.country === country && c.companyId === companyId)) {
        throw new ApiError(409, `Counterparty ${country} ${companyId} already exists`);
      }
      const c: Counterparty = {
        id: uid(), legalName: input.legalName.trim(), country, companyId,
        vatNumber: input.vatNumber?.replace(/\s/g, '').toUpperCase() || null, address: input.address || null,
        email: input.email || null, phone: input.phone || null, roles: input.roles, workTypes: input.workTypes ?? [],
        checkStatus: null, lastCheckedAt: null, createdAt: now(),
      };
      counterparties.push(c);
      record('Counterparty', c.id, 'CREATED', input);
      return delay(c);
    },
    runChecks: async (id) => {
      const c = find(id);
      const results: CheckResult[] = [];
      const add = (source: CheckResult['source'], findings: CheckFinding[], requestId: string | null = null) =>
        results.push({ id: uid(), source, status: statusOf(findings), findings, requestId,
          unavailableReason: null, checkedAt: now(), checkedAccount: null });
      if (c.country === 'CZ' && c.companyId) { add('ARES', []); add('REGISTR_DPH', []); }
      if (c.country === 'PL' && c.companyId) { add('BIALA_LISTA', [], `DEMO-WL-${Date.now().toString(36)}`); add('KRS', []); }
      if (c.vatNumber && c.country !== 'MD') add('VIES', [], `DEMO${Date.now().toString(36).toUpperCase()}`);
      const all = results.flatMap((r) => r.findings);
      const run: CheckRun = results.length
        ? { status: results.reduce<CheckStatus>((s, r) => (SEVERITY[r.status] > SEVERITY[s] ? r.status : s), 'GREEN'),
            findings: all, results }
        : { status: 'YELLOW', findings: ['MANUAL_REVIEW_REQUIRED'], results: [] };
      c.checkStatus = run.status;
      c.lastCheckedAt = now();
      checks.set(id, [...results, ...(checks.get(id) ?? [])]);
      record('Counterparty', id, 'CHECKED', { status: run.status });
      return delay(run);
    },
    checkAccount: async (id, account) => {
      const c = find(id);
      const compact = account.replace(/\s/g, '').toUpperCase();
      // Demo rule: accounts containing 999 are "not published"; countries without a registry need manual review.
      const findings: CheckFinding[] = !['CZ', 'PL'].includes(c.country) ? ['MANUAL_REVIEW_REQUIRED']
        : compact.includes('999') ? ['ACCOUNT_NOT_PUBLISHED'] : [];
      const result: CheckResult = {
        id: uid(), source: c.country === 'PL' ? 'BIALA_LISTA' : c.country === 'CZ' ? 'REGISTR_DPH' : 'MANUAL',
        status: statusOf(findings), findings, requestId: c.country === 'PL' ? `DEMO-WL-${Date.now().toString(36)}` : null,
        unavailableReason: null, checkedAt: now(), checkedAccount: compact,
      };
      checks.set(id, [result, ...(checks.get(id) ?? [])]);
      record('Counterparty', id, 'ACCOUNT_CHECKED', { account: compact, status: result.status });
      return delay(result);
    },
    checkHistory: async (id) => {
      find(id);
      return delay(checks.get(id) ?? []);
    },
    lookup: async (country, companyId) => {
      if (country.toUpperCase() !== 'CZ' || !companyId) return delay(null);
      return delay({ source: 'ARES' as const, legalName: `Demo firma ${companyId} s.r.o.`,
        address: 'Václavské náměstí 1, 110 00 Praha 1', vatNumber: `CZ${companyId}`, registeredOn: '2019-05-14' });
    },
    verifyAudit: () => delay({ valid: true, verifiedRecords: audit.length, brokenAtSeq: null }),
    auditHistory: (type, id) => delay(audit.filter((a) => a.entityType === type && a.entityId === id)),
    workTypes: () => delay(workTypes),
    setWorkTypes: async (id, codes) => {
      const c = find(id);
      c.workTypes = [...new Set(codes)].sort();
      record('Counterparty', id, 'WORK_TYPES_CHANGED', { workTypes: c.workTypes });
      return delay(c);
    },
  };
  const withEvidence = { ...base, ...createEvidenceMock(base, counterparties, audit, record, delay) };
  const withImport = { ...withEvidence, ...createImportMock(withEvidence, counterparties, record, delay) };
  return { ...withImport, ...createAnalyticsMock(withImport, counterparties, delay) };
}
