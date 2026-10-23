import type {
  Api, Counterparty, Deal, MarginCut, MarginFilter, MarginReport, MarginRow, PartyRisk, PackageRisk,
} from './types';
import { ApiError } from './http';
import { localToday } from '../dates';

/** Analytics for the demo API: the same cuts and rules as the backend's AnalyticsService and MarginCuts. */

export type AnalyticsApi = Pick<Api, 'analyticsMargin' | 'analyticsHealth'>;

export const NONE = 'NONE';
const CONTRACTED = ['WON', 'IN_PROGRESS', 'COMPLETED'];
const RUNNING = ['WON', 'IN_PROGRESS'];
/** Demo rates, CZK per unit; the server uses the day's ČNB rates. */
const DEMO_RATES: Record<string, number> = { CZK: 1, EUR: 25.2, PLN: 5.9, USD: 23.1 };

interface Line { workType: string | null; subId: string | null; subName: string | null; cost: number; price: number }
interface DealIn {
  id: string; number: string; title: string; customerId: string; customerName: string; direction: string;
  country: string | null; month: string; lines: Line[];
}

const round2 = (v: number) => Math.round(v * 100) / 100;
const pct = (margin: number, price: number) => (price === 0 ? null : Math.round((margin / price) * 1000) / 10);

export function marginCuts(deals: DealIn[]): Pick<MarginReport, 'totals' | 'cuts'> {
  const group = (rows: Map<string, { label: string; sub: string | null; deals: Set<string>; price: number; cost: number }>,
    key: string, label: string, sub: string | null, dealId: string, l: Line[]) => {
    const g = rows.get(key) ?? { label, sub, deals: new Set<string>(), price: 0, cost: 0 };
    g.deals.add(dealId);
    l.forEach((x) => { g.price += x.price; g.cost += x.cost; });
    rows.set(key, g);
  };
  const out = (rows: Map<string, { label: string; sub: string | null; deals: Set<string>; price: number; cost: number }>,
    byKey = false): MarginRow[] => [...rows.entries()].map(([key, g]) => {
    const price = round2(g.price);
    const cost = round2(g.cost);
    return { key, label: g.label, sub: g.sub, deals: g.deals.size, price, cost, margin: round2(price - cost),
      marginPct: pct(price - cost, price) };
  }).sort((a, b) => (byKey ? a.key.localeCompare(b.key) : b.margin - a.margin || a.key.localeCompare(b.key)));

  const by = (key: (d: DealIn) => string, label: (d: DealIn) => string, sub: (d: DealIn) => string | null = () => null) => {
    const rows = new Map();
    deals.forEach((d) => group(rows, key(d), label(d), sub(d), d.id, d.lines));
    return rows;
  };
  const byLine = (key: (l: Line) => string, label: (l: Line) => string) => {
    const rows = new Map();
    deals.forEach((d) => d.lines.forEach((l) => group(rows, key(l), label(l), null, d.id, [l])));
    return rows;
  };
  const cuts: Record<MarginCut, MarginRow[]> = {
    DEAL: out(by((d) => d.id, (d) => d.number, (d) => `${d.title} · ${d.customerName}`)),
    CUSTOMER: out(by((d) => d.customerId, (d) => d.customerName)),
    SUBCONTRACTOR: out(byLine((l) => l.subId ?? NONE, (l) => (l.subId ? l.subName ?? l.subId : NONE))),
    WORK_TYPE: out(byLine((l) => l.workType ?? NONE, (l) => l.workType ?? NONE)),
    DIRECTION: out(by((d) => d.direction, (d) => d.direction)),
    COUNTRY: out(by((d) => d.country ?? NONE, (d) => d.country ?? NONE)),
    MONTH: out(by((d) => d.month, (d) => d.month), true),
  };
  const price = round2(deals.reduce((s, d) => s + d.lines.reduce((x, l) => x + l.price, 0), 0));
  const cost = round2(deals.reduce((s, d) => s + d.lines.reduce((x, l) => x + l.cost, 0), 0));
  return { totals: { deals: deals.length, price, cost, margin: round2(price - cost), marginPct: pct(price - cost, price) }, cuts };
}

export function createAnalyticsMock(api: Omit<Api, keyof AnalyticsApi>, counterparties: Counterparty[],
  delay: <T>(v: T) => Promise<T>): AnalyticsApi {
  const dealsIn = async (statuses: string[]): Promise<Deal[]> => {
    const list = (await api.listDeals()).filter((d) => statuses.includes(d.status));
    return Promise.all(list.map((d) => api.getDeal(d.id)));
  };

  return {
    analyticsMargin: async (f: MarginFilter = {}) => {
      const target = (f.currency || 'CZK').toUpperCase();
      if (!DEMO_RATES[target]) throw new ApiError(400, `No exchange rate for ${target} today`);
      if (f.from && f.to && f.from > f.to) throw new ApiError(400, 'The period starts after it ends');
      const deals = (await dealsIn(f.scope === 'PIPELINE' ? ['QUOTED'] : CONTRACTED))
        .filter((d) => !f.direction || d.direction === f.direction)
        .filter((d) => !f.country || d.siteCountry === f.country)
        .filter((d) => !f.from || d.createdAt.slice(0, 10) >= f.from)
        .filter((d) => !f.to || d.createdAt.slice(0, 10) <= f.to);
      const used = new Set<string>([target]);
      const input: DealIn[] = [];
      for (const d of deals) {
        const rate = DEMO_RATES[d.currency];
        if (!rate) continue;
        used.add(d.currency);
        const conv = (v: number) => round2((v * rate) / DEMO_RATES[target]);
        const approved = (await api.changeOrders(d.id)).filter((c) => c.status === 'APPROVED').flatMap((c) => c.lines);
        input.push({ id: d.id, number: d.number, title: d.title, customerId: d.customer?.id ?? '',
          customerName: d.customer?.legalName ?? '', direction: d.direction, country: d.siteCountry,
          month: d.createdAt.slice(0, 7),
          lines: [...d.lines, ...approved].map((l) => ({ workType: l.workType, subId: l.subcontractor?.id ?? null,
            subName: l.subcontractor?.legalName ?? null, cost: conv(l.costTotal), price: conv(l.priceTotal) })) });
      }
      const today = localToday();
      return delay({ currency: target, ratesOn: today,
        rates: [...used].map((c) => ({ currency: c, czkPerUnit: DEMO_RATES[c], validFor: today, source: 'demo' })),
        missingRates: [], ...marginCuts(input) });
    },
    analyticsHealth: async () => {
      const deals = await dealsIn(CONTRACTED);
      const byId = new Map(deals.map((d) => [d.id, d]));
      const sites = (await api.listSites({})).filter((s) => s.status !== 'DONE' || s.openDefects > 0);
      const today = localToday();
      const siteRows = await Promise.all(sites.map(async (s) => {
        const d = byId.get(s.dealId);
        const defects = (await api.siteDefects(s.id)).filter((x) => x.status === 'OPEN' || x.status === 'FIXED');
        const pending = d ? (await api.changeOrders(d.id)).filter((c) => c.status === 'SENT') : [];
        return { siteId: s.id, dealId: s.dealId, dealNumber: s.dealNumber, name: s.name, city: s.city, country: s.country,
          status: s.status, plannedEnd: s.plannedEnd, progressPct: s.progressPct, lateTasks: s.lateTasks,
          openDefects: defects.length, overdueDefects: defects.filter((x) => x.dueDate && x.dueDate < today).length,
          pendingChangeOrders: pending.length, pendingChangePrice: round2(pending.reduce((x, c) => x + c.totals.price, 0)),
          currency: d?.currency ?? null };
      }));
      siteRows.sort((a, b) => (b.lateTasks + b.overdueDefects) - (a.lateTasks + a.overdueDefects) || b.openDefects - a.openDefects);

      const parties = new Map<string, PartyRisk>();
      const add = (id: string | undefined | null, role: 'CUSTOMER' | 'SUBCONTRACTOR', number: string) => {
        const c = counterparties.find((x) => x.id === id);
        if (!c) return;
        const p = parties.get(c.id) ?? { id: c.id, name: c.legalName, country: c.country, status: c.checkStatus,
          roles: [], deals: [] };
        if (!p.roles.includes(role)) p.roles.push(role);
        if (!p.deals.includes(number)) p.deals.push(number);
        parties.set(c.id, p);
      };
      deals.filter((d) => RUNNING.includes(d.status)).forEach((d) => {
        add(d.customer?.id, 'CUSTOMER', d.number);
        d.lines.forEach((l) => add(l.subcontractor?.id, 'SUBCONTRACTOR', d.number));
      });
      const rank = (s: string | null) => (s === 'RED' ? 2 : s === null ? 1 : 0);
      const risky = [...parties.values()].filter((p) => p.status !== 'GREEN')
        .sort((a, b) => rank(b.status) - rank(a.status) || a.name.localeCompare(b.name));

      const packages: PackageRisk[] = (await Promise.all(deals.map(async (d) => {
        const gaps = (await api.evidence(d.id)).checks.filter((c) => c.level !== 'OK' && c.code !== 'AUDIT_CHAIN');
        const missing = gaps.filter((c) => c.level === 'MISSING').length;
        return { dealId: d.id, number: d.number, title: d.title, status: d.status, missing, warn: gaps.length - missing,
          gaps };
      }))).filter((p) => p.gaps.length > 0);
      packages.sort((a, b) => b.missing - a.missing || b.warn - a.warn || a.number.localeCompare(b.number));
      return delay({ sites: siteRows, counterparties: risky, packages });
    },
  };
}
