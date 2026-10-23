import type { Api, Counterparty, DealStatus, Site, SiteParty, SiteTask, TaskData, WorkloadRow } from './types';
import { ApiError } from './http';
import { addDays, daysInclusive, localToday, mondayOf } from '../dates';

/** Sites, schedules and workload for the demo API; mirrors the backend's SiteService rules. */

export type SitesApi = Pick<Api, 'config' | 'listSites' | 'getSite' | 'createSite' | 'editSite' | 'addTask'
  | 'updateTask' | 'removeTask' | 'tasksFromDeal' | 'workload'>;

export interface DealAccess {
  find(id: string): { id: string; number: string; title: string; status: DealStatus; siteCountry: string | null;
    customerId: string; lines: { id: string; kind: string; description: string; workType?: string | null;
      subcontractorId?: string | null }[] } | undefined;
}

interface StoredTask extends Omit<SiteTask, 'subcontractor' | 'late'> { subcontractorId: string | null }
interface StoredSite extends Omit<Site, 'dealNumber' | 'dealStatus' | 'customer' | 'progressPct' | 'lateTasks' | 'workTypes'
  | 'tasks'> { tasks: StoredTask[] }

const days = (t: { start: string; end: string }) => daysInclusive(t.start, t.end);

export function createSitesMock(
  counterparties: Counterparty[], deals: DealAccess,
  record: (type: string, id: string, action: string, payload: unknown) => void,
  delay: <T>(v: T) => Promise<T>,
): SitesApi & { seed(dealId: string, s: Partial<StoredSite>, tasks: Omit<StoredTask, 'id' | 'position'>[]): void } {
  const sites: StoredSite[] = [];
  const today = localToday;

  const party = (id: string | null | undefined): SiteParty | null => {
    const c = counterparties.find((x) => x.id === id);
    return c ? { id: c.id, legalName: c.legalName, country: c.country, checkStatus: c.checkStatus } : null;
  };
  const find = (id: string) => {
    const s = sites.find((x) => x.id === id);
    if (!s) throw new ApiError(404, `Site ${id} not found`);
    return s;
  };
  const requireSub = (id: string | null) => {
    if (!id) return;
    const c = counterparties.find((x) => x.id === id);
    if (!c || !c.roles.some((r) => r !== 'CUSTOMER')) {
      throw new ApiError(400, `${c?.legalName ?? id} is not a subcontractor, supplier or carrier`);
    }
  };
  const checkTask = (t: TaskData) => {
    if (!t.title.trim()) throw new ApiError(400, 'A task needs a title');
    if (!t.start || !t.end || t.end < t.start) throw new ApiError(400, 'A task ends on or after its start');
    if (t.progressPct < 0 || t.progressPct > 100) throw new ApiError(400, 'Progress is 0 to 100 %');
  };

  const view = (s: StoredSite): Site => {
    const deal = deals.find(s.dealId);
    const total = s.tasks.reduce((n, t) => n + days(t), 0);
    const done = s.tasks.reduce((n, t) => n + days(t) * t.progressPct, 0);
    const now = today();
    const workTypes = new Set<string>();
    s.tasks.forEach((t) => t.workType && workTypes.add(t.workType));
    deal?.lines.forEach((l) => l.workType && workTypes.add(l.workType));
    return {
      ...s, dealNumber: deal?.number ?? null, dealStatus: deal?.status ?? null,
      customer: party(deal?.customerId), progressPct: total ? Math.round(done / total) : s.status === 'DONE' ? 100 : 0,
      lateTasks: s.tasks.filter((t) => t.progressPct < 100 && t.end < now).length,
      workTypes: [...workTypes].sort(),
      tasks: s.tasks.map(({ subcontractorId, ...t }) => ({ ...t, subcontractor: party(subcontractorId),
        late: t.progressPct < 100 && t.end < now })),
    };
  };
  const nextPos = (s: StoredSite) => s.tasks.reduce((m, t) => Math.max(m, t.position), 0) + 1;

  return {
    seed(dealId, s, tasks) {
      const deal = deals.find(dealId)!;
      const site: StoredSite = {
        id: crypto.randomUUID(), dealId, name: deal.title, address: null, city: null, country: deal.siteCountry,
        latitude: null, longitude: null, projectManager: null, foreman: null, plannedStart: null, plannedEnd: null,
        status: 'PLANNED', notes: null, createdAt: '2026-09-20T08:00:00Z', tasks: [], ...s,
      };
      tasks.forEach((t, i) => site.tasks.push({ ...t, id: crypto.randomUUID(), position: i + 1 }));
      sites.push(site);
    },
    config: () => delay({ googleMapsApiKey: (import.meta.env.VITE_DEMO_GOOGLE_MAPS_KEY as string | undefined) ?? null,
      googleMapsMapId: null }),
    listSites: async (f = {}) => {
      const out = sites.map(view).filter((s) => (!f.status || s.status === f.status)
        && (!f.country || s.country === f.country.toUpperCase())
        && (!f.city || s.city?.toLowerCase() === f.city.toLowerCase())
        && (!f.pm || s.projectManager?.toLowerCase() === f.pm.toLowerCase())
        && (!f.customerId || s.customer?.id === f.customerId)
        && (!f.workType || s.workTypes.includes(f.workType))
        && (!f.dealId || s.dealId === f.dealId));
      return delay(out.map((s) => ({ ...s, tasks: [] })));
    },
    getSite: async (id) => delay(view(find(id))),
    createSite: async (dealId) => {
      const deal = deals.find(dealId);
      if (!deal) throw new ApiError(404, `Deal ${dealId} not found`);
      if (!['WON', 'IN_PROGRESS'].includes(deal.status)) {
        throw new ApiError(409, `A site is opened once the customer accepted deal ${deal.number}`);
      }
      if (sites.some((s) => s.dealId === dealId)) throw new ApiError(409, `Deal ${deal.number} already has a site`);
      const site: StoredSite = {
        id: crypto.randomUUID(), dealId, name: deal.title, address: null, city: null, country: deal.siteCountry,
        latitude: null, longitude: null, projectManager: null, foreman: null, plannedStart: null, plannedEnd: null,
        status: 'PLANNED', notes: null, createdAt: new Date().toISOString(), tasks: [],
      };
      sites.push(site);
      record('Site', site.id, 'CREATED', { dealId });
      return delay(view(site));
    },
    editSite: async (id, d) => {
      const s = find(id);
      if (!d.name.trim()) throw new ApiError(400, 'A site needs a name');
      if ((d.latitude == null) !== (d.longitude == null)) throw new ApiError(400, 'Give both latitude and longitude, or neither');
      if (d.plannedStart && d.plannedEnd && d.plannedEnd < d.plannedStart) throw new ApiError(400, 'The site ends on or after its start');
      if (d.country && !/^[A-Za-z]{2}$/.test(d.country)) throw new ApiError(400, 'Country is a two-letter code');
      Object.assign(s, { ...d, country: d.country?.toUpperCase() || null });
      record('Site', id, 'EDITED', d);
      return delay(view(s));
    },
    addTask: async (siteId, t) => {
      const s = find(siteId);
      checkTask(t);
      requireSub(t.subcontractorId);
      s.tasks.push({ ...t, title: t.title.trim(), id: crypto.randomUUID(), position: nextPos(s), dealLineId: null });
      record('Site', siteId, 'TASK_ADDED', t);
      return delay(view(s));
    },
    updateTask: async (siteId, taskId, t) => {
      const s = find(siteId);
      checkTask(t);
      requireSub(t.subcontractorId);
      const task = s.tasks.find((x) => x.id === taskId);
      if (!task) throw new ApiError(400, `Task ${taskId} is not on site ${s.name}`);
      Object.assign(task, { ...t, title: t.title.trim() });
      record('Site', siteId, 'TASK_UPDATED', t);
      return delay(view(s));
    },
    removeTask: async (siteId, taskId) => {
      const s = find(siteId);
      s.tasks = s.tasks.filter((t) => t.id !== taskId);
      record('Site', siteId, 'TASK_REMOVED', { taskId });
      return delay(view(s));
    },
    tasksFromDeal: async (siteId) => {
      const s = find(siteId);
      if (!s.plannedStart || !s.plannedEnd) throw new ApiError(409, 'Set the planned start and end of the site first');
      const linked = new Set(s.tasks.map((t) => t.dealLineId));
      deals.find(s.dealId)?.lines.filter((l) => l.kind === 'WORK' && !linked.has(l.id)).forEach((l) => {
        s.tasks.push({ id: crypto.randomUUID(), position: nextPos(s), title: l.description, workType: l.workType ?? null,
          dealLineId: l.id, subcontractorId: l.subcontractorId ?? null, start: s.plannedStart!, end: s.plannedEnd!,
          progressPct: 0 });
      });
      record('Site', siteId, 'TASKS_FROM_DEAL', {});
      return delay(view(s));
    },
    workload: async (from, to) => {
      if (to < from) throw new ApiError(400, 'Choose a period of up to about a year');
      const bySub = new Map<string, WorkloadRow['tasks']>();
      sites.forEach((s) => s.tasks.forEach((t) => {
        if (!t.subcontractorId || t.start > to || t.end < from) return;
        const list = bySub.get(t.subcontractorId) ?? [];
        list.push({ taskId: t.id, title: t.title, siteId: s.id, siteName: s.name, start: t.start, end: t.end,
          progressPct: t.progressPct });
        bySub.set(t.subcontractorId, list);
      }));
      const rows: WorkloadRow[] = [];
      bySub.forEach((tasks, subId) => {
        const sub = party(subId);
        if (!sub) return;
        const weeks: WorkloadRow['weeks'] = [];
        for (let m = mondayOf(from); m <= to; m = addDays(m, 7)) {
          let peak = 0;
          for (let i = 0; i < 5; i++) {
            const day = addDays(m, i);
            peak = Math.max(peak, tasks.filter((t) => day >= t.start && day <= t.end).length);
          }
          weeks.push({ monday: m, parallelTasks: peak });
        }
        rows.push({ subcontractor: sub, tasks: tasks.sort((a, b) => a.start.localeCompare(b.start)), weeks });
      });
      return delay(rows.sort((a, b) => a.subcontractor.legalName.localeCompare(b.subcontractor.legalName)));
    },
  };
}
