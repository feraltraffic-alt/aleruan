import type {
  Api, Counterparty, DealStatus, Defect, DefectData, DefectSeverity, DefectStatus, DiaryData, DiaryEntry, FileRef, Site,
  SiteParty, SiteTask, Stage, StageStatus, TaskData, WorkloadRow,
} from './types';
import { ApiError } from './http';
import { addDays, daysInclusive, localToday, mondayOf } from '../dates';

const warrantyEnd = (from: string, months: number) => {
  const d = new Date(`${from}T00:00:00Z`);
  d.setUTCMonth(d.getUTCMonth() + months);
  return addDays(d.toISOString().slice(0, 10), -1);
};

/** Sites, schedules and workload for the demo API; mirrors the backend's SiteService rules. */

export type SitesApi = Pick<Api, 'config' | 'listSites' | 'getSite' | 'createSite' | 'editSite' | 'addTask'
  | 'updateTask' | 'removeTask' | 'tasksFromDeal' | 'workload' | 'setHandover' | 'diary' | 'addDiaryEntry'
  | 'editDiaryEntry' | 'siteDefects' | 'unresolvedDefects' | 'reportDefect' | 'editDefect' | 'moveDefect'
  | 'uploadPhoto' | 'removePhoto' | 'fileBlob' | 'stages' | 'createStage' | 'editStage' | 'removeStage'
  | 'stageAction' | 'decideStage' | 'protocolFile'>;

const STAGE_NEXT: Record<StageStatus, StageStatus[]> = {
  PLANNED: ['SUBMITTED'], SUBMITTED: ['ACCEPTED', 'ACCEPTED_WITH_REMARKS', 'REJECTED', 'PLANNED'],
  REJECTED: ['SUBMITTED'], ACCEPTED: [], ACCEPTED_WITH_REMARKS: [],
};

export interface DealAccess {
  find(id: string): { id: string; number: string; title: string; status: DealStatus; siteCountry: string | null;
    customerId: string; lines: { id: string; kind: string; description: string; workType?: string | null;
      subcontractorId?: string | null }[] } | undefined;
  contractValue(dealId: string): number;
  currency(dealId: string): string;
}

interface StoredTask extends Omit<SiteTask, 'subcontractor' | 'late'> { subcontractorId: string | null }
interface StoredSite extends Omit<Site, 'dealNumber' | 'dealStatus' | 'customer' | 'progressPct' | 'lateTasks' | 'workTypes'
  | 'tasks' | 'warrantyUntil' | 'openDefects'> { tasks: StoredTask[] }

const days = (t: { start: string; end: string }) => daysInclusive(t.start, t.end);

export function createSitesMock(
  counterparties: Counterparty[], deals: DealAccess,
  record: (type: string, id: string, action: string, payload: unknown) => void,
  delay: <T>(v: T) => Promise<T>,
): SitesApi & { seed(dealId: string, s: Partial<StoredSite>, tasks: Omit<StoredTask, 'id' | 'position'>[]): void } {
  const sites: StoredSite[] = [];
  const stages: Stage[] = [];
  const today = localToday;
  const diary: (Omit<DiaryEntry, 'correctedBy' | 'editable' | 'photos'> & { siteId: string })[] = [];
  const defects: (Omit<Defect, 'siteName' | 'nextStatuses' | 'overdue' | 'photos' | 'subcontractor'>
    & { subcontractorId: string | null })[] = [];
  const files: (FileRef & { ownerId: string; blob: Blob })[] = [];
  const photosOf = (ownerId: string): FileRef[] =>
    files.filter((f) => f.ownerId === ownerId).map(({ ownerId: _o, blob: _b, ...f }) => f);
  const DEFECT_NEXT: Record<DefectStatus, DefectStatus[]> = {
    OPEN: ['FIXED', 'DISMISSED'], FIXED: ['CLOSED', 'OPEN'], CLOSED: ['OPEN'], DISMISSED: ['OPEN'],
  };
  const SEVERITY_DAYS: Record<DefectSeverity, number> = { MINOR: 14, MAJOR: 7, CRITICAL: 2 };
  const diaryView = (e: (typeof diary)[number]): DiaryEntry => {
    const { siteId: _s, ...rest } = e;
    return { ...rest, correctedBy: diary.filter((x) => x.correctsId === e.id).map((x) => x.id),
      editable: e.author === 'demo' && Date.now() - new Date(e.createdAt).getTime() < 86_400_000, photos: photosOf(e.id) };
  };
  const defectView = (d: (typeof defects)[number]): Defect => {
    const { subcontractorId, ...rest } = d;
    const sub = party(subcontractorId);
    return { ...rest, siteName: sites.find((x) => x.id === d.siteId)?.name ?? '', nextStatuses: DEFECT_NEXT[d.status],
      overdue: d.status === 'OPEN' && !!d.dueDate && d.dueDate < today(), photos: photosOf(d.id),
      subcontractor: sub ? { id: sub.id, legalName: sub.legalName, checkStatus: sub.checkStatus } : null };
  };
  const checkDiary = (d: DiaryData) => {
    if (!d.entryDate || d.entryDate > today()) throw new ApiError(400, 'A diary entry is about today or an earlier day');
    if (!d.text.trim()) throw new ApiError(400, 'Write what happened on site');
    if (d.workers != null && (d.workers < 0 || d.workers > 1000)) throw new ApiError(400, 'Number of workers is 0 to 1000');
  };
  const defectFields = (siteId: string, d: DefectData) => {
    if (!d.title.trim()) throw new ApiError(400, 'Name the defect');
    if (!d.foundOn) throw new ApiError(400, 'When was it found?');
    if (d.dueDate && d.dueDate < d.foundOn) throw new ApiError(400, 'The fix is due on or after the day the defect was found');
    if (d.subcontractorId) {
      const c = counterparties.find((x) => x.id === d.subcontractorId);
      if (!c || !c.roles.some((r) => r === 'SUBCONTRACTOR' || r === 'SUPPLIER')) {
        throw new ApiError(400, `${c?.legalName ?? d.subcontractorId} is not a subcontractor or supplier`);
      }
    }
    const handover = find(siteId).handoverDate;
    return { ...d, title: d.title.trim(), dueDate: d.dueDate || addDays(d.foundOn, SEVERITY_DAYS[d.severity]),
      warranty: !!handover && d.foundOn > handover };
  };
  const findDefect = (id: string) => {
    const d = defects.find((x) => x.id === id);
    if (!d) throw new ApiError(404, `Defect ${id} not found`);
    return d;
  };

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
      warrantyUntil: s.handoverDate && s.warrantyMonths != null ? warrantyEnd(s.handoverDate, s.warrantyMonths) : null,
      openDefects: defects.filter((d) => d.siteId === s.id && (d.status === 'OPEN' || d.status === 'FIXED')).length,
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
        status: 'PLANNED', notes: null, createdAt: '2026-09-20T08:00:00Z', tasks: [], handoverDate: null,
        warrantyMonths: null, ...s,
      };
      tasks.forEach((t, i) => site.tasks.push({ ...t, id: crypto.randomUUID(), position: i + 1 }));
      sites.push(site);
    },
    setHandover: async (siteId, date, months) => {
      const s = find(siteId);
      if (date && date > today()) throw new ApiError(400, 'Handover is recorded once it has happened');
      if (months != null && (months < 0 || months > 120)) throw new ApiError(400, 'Warranty is 0 to 120 months');
      if (date && s.plannedStart && date < s.plannedStart) throw new ApiError(400, 'Handover cannot be before the site started');
      s.handoverDate = date;
      s.warrantyMonths = date ? months ?? 24 : null;
      record('Site', siteId, 'HANDOVER', { date, months });
      return delay(view(s));
    },
    diary: async (siteId) => {
      find(siteId);
      return delay(diary.filter((e) => e.siteId === siteId)
        .sort((a, b) => b.entryDate.localeCompare(a.entryDate) || b.createdAt.localeCompare(a.createdAt)).map(diaryView));
    },
    addDiaryEntry: async (siteId, d) => {
      find(siteId);
      checkDiary(d);
      const e = { ...d, id: crypto.randomUUID(), siteId, text: d.text.trim(), weather: d.weather?.trim() || null,
        author: 'demo', createdAt: new Date().toISOString(), editedAt: null };
      diary.push(e);
      record('DiaryEntry', e.id, 'CREATED', d);
      return delay(diaryView(e));
    },
    editDiaryEntry: async (id, d) => {
      const e = diary.find((x) => x.id === id);
      if (!e) throw new ApiError(404, `Diary entry ${id} not found`);
      if (!diaryView(e).editable) {
        throw new ApiError(409, 'Diary entries can be changed by their author for 24 hours; add a correction instead');
      }
      checkDiary(d);
      Object.assign(e, { entryDate: d.entryDate, weather: d.weather?.trim() || null, workers: d.workers,
        text: d.text.trim(), editedAt: new Date().toISOString() });
      record('DiaryEntry', id, 'EDITED', d);
      return delay(diaryView(e));
    },
    siteDefects: async (siteId) => {
      find(siteId);
      return delay(defects.filter((d) => d.siteId === siteId).sort((a, b) => b.seq - a.seq).map(defectView));
    },
    unresolvedDefects: async () => delay(defects.filter((d) => d.status === 'OPEN' || d.status === 'FIXED')
      .sort((a, b) => (a.dueDate ?? '9').localeCompare(b.dueDate ?? '9')).map(defectView)),
    reportDefect: async (siteId, data) => {
      const fields = defectFields(siteId, data);
      const d = { ...fields, id: crypto.randomUUID(), siteId,
        seq: defects.filter((x) => x.siteId === siteId).reduce((m, x) => Math.max(m, x.seq), 0) + 1,
        status: 'OPEN' as DefectStatus, fixedAt: null, fixNote: null, closedAt: null, closedBy: null,
        createdAt: new Date().toISOString(), createdBy: 'demo' };
      defects.push(d);
      record('Defect', d.id, 'REPORTED', data);
      return delay(defectView(d));
    },
    editDefect: async (id, data) => {
      const d = findDefect(id);
      Object.assign(d, defectFields(d.siteId, data));
      record('Defect', id, 'EDITED', data);
      return delay(defectView(d));
    },
    moveDefect: async (id, status, note) => {
      const d = findDefect(id);
      if (!DEFECT_NEXT[d.status].includes(status)) throw new ApiError(409, `Defect #${d.seq} cannot move from ${d.status} to ${status}`);
      if ((status === 'DISMISSED' || (d.status === 'FIXED' && status === 'OPEN')) && !note?.trim()) throw new ApiError(400, 'Say why');
      const now = new Date().toISOString();
      if (status === 'FIXED') Object.assign(d, { fixedAt: now, fixNote: note?.trim() || null });
      if (status === 'CLOSED' || status === 'DISMISSED') Object.assign(d, { closedAt: now, closedBy: 'demo',
        ...(status === 'DISMISSED' ? { fixNote: note?.trim() || null } : {}) });
      if (status === 'OPEN') Object.assign(d, { closedAt: null, closedBy: null, fixedAt: null, fixNote: note?.trim() || null });
      d.status = status;
      record('Defect', id, 'STATUS_CHANGED', { status, note });
      return delay(defectView(d));
    },
    uploadPhoto: async (owner, ownerId, file, fileName) => {
      if (owner === 'diary' && !diary.some((e) => e.id === ownerId)) throw new ApiError(404, 'Diary entry not found');
      if (owner === 'defects') findDefect(ownerId);
      const type = file.type;
      if (!['image/jpeg', 'image/png', 'image/webp', 'application/pdf'].includes(type)) {
        throw new ApiError(400, 'Only photos (JPEG, PNG, WebP) and PDF files can be attached');
      }
      if (file.size > 12 * 1024 * 1024) throw new ApiError(400, 'The file is larger than 12 MB');
      const f = { id: crypto.randomUUID(), fileName, contentType: type, sizeBytes: file.size,
        uploadedAt: new Date().toISOString(), uploadedBy: 'demo', ownerId, blob: file };
      files.push(f);
      const { ownerId: _o, blob: _b, ...ref } = f;
      return delay(ref);
    },
    removePhoto: async (_owner, ownerId, fileId) => {
      const i = files.findIndex((f) => f.id === fileId && f.ownerId === ownerId);
      if (i < 0) throw new ApiError(404, 'File not found');
      files.splice(i, 1);
      return delay(undefined);
    },
    fileBlob: async (id) => {
      const f = files.find((x) => x.id === id);
      if (!f) throw new ApiError(404, 'File not found');
      return f.blob;
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
        status: 'PLANNED', notes: null, createdAt: new Date().toISOString(), tasks: [], handoverDate: null,
        warrantyMonths: null,
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
    stages: async (siteId) => {
      const s = find(siteId);
      const mine = stages.filter((x) => x.siteId === siteId).sort((a, b) => a.seq - b.seq);
      const contract = deals.contractValue(s.dealId);
      const planned = mine.reduce((n, x) => n + x.amount, 0);
      const accepted = mine.filter((x) => x.status.startsWith('ACCEPTED')).reduce((n, x) => n + x.amount, 0);
      return delay({ stages: mine.map((x) => ({ ...x, nextStatuses: STAGE_NEXT[x.status] })),
        coverage: { contractValue: contract, planned, accepted, unassigned: Math.round((contract - planned) * 100) / 100,
          currency: deals.currency(s.dealId) } });
    },
    createStage: async (siteId, d) => {
      const s = find(siteId);
      if (!d.title.trim()) throw new ApiError(400, 'A stage needs a title');
      if (!(d.amount >= 0)) throw new ApiError(400, 'The stage amount is zero or more');
      if (d.taskIds.some((id) => !s.tasks.some((t) => t.id === id))) throw new ApiError(400, `Some tasks are not on site ${s.name}`);
      const stage: Stage = { id: crypto.randomUUID(), siteId, seq: stages.filter((x) => x.siteId === siteId).length + 1,
        ...d, title: d.title.trim(), status: 'PLANNED', nextStatuses: STAGE_NEXT.PLANNED, submittedAt: null,
        decidedAt: null, decidedBy: null, remarks: null, fixUntil: null };
      stages.push(stage);
      record('AcceptanceStage', stage.id, 'CREATED', d);
      return delay(stage);
    },
    editStage: async (id, d) => {
      const st = stages.find((x) => x.id === id);
      if (!st) throw new ApiError(404, `Stage ${id} not found`);
      if (st.status !== 'PLANNED' && st.status !== 'REJECTED') throw new ApiError(409, `Stage ${st.seq} was handed over; withdraw it to change it`);
      Object.assign(st, { ...d, title: d.title.trim() });
      return delay({ ...st, nextStatuses: STAGE_NEXT[st.status] });
    },
    removeStage: async (id) => {
      const i = stages.findIndex((x) => x.id === id);
      if (i < 0) throw new ApiError(404, `Stage ${id} not found`);
      if (stages[i].status !== 'PLANNED') throw new ApiError(409, 'Only a planned stage can be deleted');
      stages.splice(i, 1);
      return delay(undefined);
    },
    stageAction: async (id, action) => {
      const st = stages.find((x) => x.id === id);
      if (!st) throw new ApiError(404, `Stage ${id} not found`);
      const to: StageStatus = action === 'submit' ? 'SUBMITTED' : 'PLANNED';
      if (!STAGE_NEXT[st.status].includes(to)) throw new ApiError(409, `Stage ${st.seq} cannot move from ${st.status} to ${to}`);
      Object.assign(st, { status: to, submittedAt: action === 'submit' ? new Date().toISOString() : null,
        decidedAt: null, decidedBy: null });
      record('AcceptanceStage', id, action.toUpperCase(), {});
      return delay({ ...st, nextStatuses: STAGE_NEXT[st.status] });
    },
    decideStage: async (id, d) => {
      const st = stages.find((x) => x.id === id);
      if (!st) throw new ApiError(404, `Stage ${id} not found`);
      if (!d.by.trim()) throw new ApiError(400, "Record who decided on the customer's side");
      if (d.decision !== 'ACCEPTED' && !d.remarks?.trim()) throw new ApiError(400, "Write down the customer's remarks or reasons");
      if (!STAGE_NEXT[st.status].includes(d.decision)) throw new ApiError(409, `Stage ${st.seq} cannot move from ${st.status} to ${d.decision}`);
      Object.assign(st, { status: d.decision, decidedBy: d.by.trim(), remarks: d.remarks?.trim() || null,
        fixUntil: d.decision === 'ACCEPTED_WITH_REMARKS' ? d.fixUntil : null, decidedAt: new Date().toISOString() });
      record('AcceptanceStage', id, 'DECIDED', d);
      return delay({ ...st, nextStatuses: STAGE_NEXT[st.status] });
    },
    protocolFile: async (id) => {
      const st = stages.find((x) => x.id === id);
      if (!st) throw new ApiError(404, `Stage ${id} not found`);
      return new Blob([`<!doctype html><meta charset="utf-8"><body style="font-family:sans-serif;padding:32px">`
        + `<p>Demo: the real system renders the handover protocol PDF here.</p><h1>${st.title}</h1>`], { type: 'text/html' });
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
