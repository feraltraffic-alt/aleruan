import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { can } from '../permissions';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api, type Site, type SiteDetails, type SiteStatus, type SiteTask, type TaskData } from '../api';
import { Drawer } from '../components/Drawer';
import { PinPicker, SitesMap, type GeoResult } from '../components/GoogleMap';
import { LoadError } from '../components/LoadError';
import { Schedule } from '../components/Schedule';
import { Status } from '../components/Status';
import { Progress, SiteStatusTag } from '../components/siteBits';
import { SITE_STATUSES, siteTone } from '../components/siteData';
import { useWorkTypes } from '../components/dealData';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { localToday } from '../dates';
import { useI18n } from '../i18n';
import { parseNum } from '../numbers';
import { StagesSection } from './Stages';
import { DiarySection } from './SiteDiary';
import { DefectsSection } from './SiteDefects';

export function SiteDetailPage() {
  const { id = '' } = useParams();
  const { t, day } = useI18n();
  const site = useLoad(() => api.getSite(id), [id]);
  const workTypes = useWorkTypes();
  const action = useAction();
  const [editing, setEditing] = useState(false);
  const [task, setTask] = useState<SiteTask | 'new' | null>(null);
  const [today] = useState(localToday);
  const [params, setParams] = useSearchParams();
  const [handover, setHandover] = useState(false);
  const tab = (TABS as readonly string[]).includes(params.get('tab') ?? '') ? params.get('tab') as Tab : 'schedule';

  if (site.error) return <LoadError message={site.error} onRetry={site.reload} />;
  const s = site.data;
  if (!s) return <p className="empty" aria-busy="true">{t('loading')}</p>;

  const fromDeal = async () => {
    const next = await action.run(() => api.tasksFromDeal(s.id));
    if (next) site.setData(next);
  };
  const hasPin = s.latitude != null && s.longitude != null;

  return (
    <>
      <Link to="/sites" className="crumb">{t('site.back')}</Link>
      <div className="page-head">
        <div>
          <h1>{s.name}</h1>
          <p className="lead">
            <SiteStatusTag status={s.status} />{' '}
            {s.dealNumber && (can('deals.read') ? <Link to={`/deals/${s.dealId}`}>{t('site.deal', { number: s.dealNumber })}</Link> : t('site.deal', { number: s.dealNumber }))}
          </p>
        </div>
        {can('sites.write') && <button className="btn" onClick={() => setEditing(true)}>{t('site.edit')}</button>}
      </div>
      {action.error && <p className="note bad" role="alert">{action.error}</p>}

      <div className="site-top">
        <dl className="panel facts">
          <div><dt>{t('deal.col.customer')}</dt><dd>
            {s.customer ? <Link to={`/counterparties/${s.customer.id}`}>{s.customer.legalName}</Link> : '—'}
            {s.customer && <> <Status status={s.customer.checkStatus} /></>}
          </dd></div>
          <div><dt>{t('site.address')}</dt><dd>{[s.address, s.city, s.country].filter(Boolean).join(', ') || '—'}</dd></div>
          <div><dt>{t('site.pm')}</dt><dd>{s.projectManager ?? '—'}</dd></div>
          <div><dt>{t('site.foreman')}</dt><dd>{s.foreman ?? '—'}</dd></div>
          <div><dt>{t('site.dates')}</dt><dd>{s.plannedStart || s.plannedEnd ? `${day(s.plannedStart)} – ${day(s.plannedEnd)}` : '—'}</dd></div>
          <div><dt>{t('site.progress')}</dt><dd className="progress-dd">
            <Progress pct={s.progressPct} late={s.lateTasks > 0} /> {s.progressPct} %
            {s.lateTasks > 0 && <span className="sub neg">{t('site.lateTasks', { n: s.lateTasks })}</span>}
          </dd></div>
          <div><dt>{t('site.handover')}</dt><dd>
            {s.handoverDate ? (
              <>{t('site.handedOver', { date: day(s.handoverDate) })}
                {s.warrantyUntil && <span className="sub">{t('site.warrantyTill', { date: day(s.warrantyUntil) })}</span>}</>
            ) : t('site.notHandedOver')}{' '}
            {can('sites.write') && <button className="linkish" onClick={() => setHandover(true)}>{s.handoverDate ? t('deal.change') : t('site.markHandover')}</button>}
          </dd></div>
          {s.notes && <div className="wide"><dt>{t('site.notes')}</dt><dd>{s.notes}</dd></div>}
        </dl>
        {hasPin && <SingleSiteMap site={s} />}
      </div>

      <div className="tabs" role="tablist" aria-label={t('site.sections')}>
        {TABS.map((k) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'on' : ''}
            onClick={() => setParams((p) => { p.set('tab', k); return p; }, { replace: true })}>
            {t(`site.tab.${k}`)}
            {k === 'defects' && s.openDefects > 0 && <span className="count">{s.openDefects}</span>}
          </button>
        ))}
      </div>

      {tab === 'schedule' && (
        <>
      <section className="section">
        <div className="section-head">
          <div>
            <h2>{t('sched.title')}</h2>
            <p className="lead">{t('sched.lead')}</p>
          </div>
          <div className="moves">
            {can('sites.write') && <button className="btn" onClick={fromDeal} disabled={action.busy}>{t('sched.fromDeal')}</button>}
            {can('sites.write') && <button className="btn primary" onClick={() => setTask('new')}>{t('sched.add')}</button>}
          </div>
        </div>
        <div className="panel">
          {s.tasks.length === 0 ? <p className="empty">{t('sched.empty')}</p> : (
            <>
              <Schedule site={s} today={today} onOpen={can('sites.write') ? setTask : undefined} />
              <div className="table-wrap">
                <table className="lines">
                  <thead>
                    <tr>
                      <th>{t('sched.task')}</th><th>{t('line.subcontractor')}</th><th>{t('sched.period')}</th>
                      <th>{t('site.progress')}</th><th><span className="sr">{t('line.actions')}</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {s.tasks.map((x) => (
                      <tr key={x.id}>
                        <td className="name-cell">{x.title}{x.workType && <span className="sub">{workTypes.name(x.workType)}</span>}</td>
                        <td>{x.subcontractor ? (
                          <><Link to={`/counterparties/${x.subcontractor.id}`}>{x.subcontractor.legalName}</Link>
                            <span className="sub"><Status status={x.subcontractor.checkStatus} /></span></>
                        ) : <span className="sub">{t('line.noSubcontractor')}</span>}</td>
                        <td className="nowrap">{day(x.start)} – {day(x.end)}{x.late && <span className="sub neg">{t('sched.late')}</span>}</td>
                        <td className="progress-dd"><Progress pct={x.progressPct} late={x.late} /> {x.progressPct} %</td>
                        <td>{can('sites.write') && <span className="row-actions">
                          <button className="linkish" onClick={() => setTask(x)}>{t('line.editShort')}</button>
                          <button className="linkish danger" aria-label={t('line.remove', { what: x.title })}
                            onClick={async () => { const n = await action.run(() => api.removeTask(s.id, x.id)); if (n) site.setData(n); }}>
                            {t('line.removeShort')}
                          </button>
                        </span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </section>
        </>
      )}
      {tab === 'acceptance' && <StagesSection site={s} />}
      {tab === 'diary' && <DiarySection site={s} />}
      {tab === 'defects' && <DefectsSection site={s} onChanged={site.reload} />}


      {handover && <HandoverForm site={s} onClose={() => setHandover(false)}
        onSaved={(n) => { site.setData(n); setHandover(false); }} />}
      {editing && <SiteForm site={s} onClose={() => setEditing(false)} onSaved={(n) => { site.setData(n); setEditing(false); }} />}
      {task && <TaskForm site={s} task={task === 'new' ? undefined : task} onClose={() => setTask(null)}
        onSaved={(n) => { site.setData(n); setTask(null); }} />}
    </>
  );
}

const TABS = ['schedule', 'acceptance', 'diary', 'defects'] as const;
type Tab = (typeof TABS)[number];

function HandoverForm({ site, onClose, onSaved }: { site: Site; onClose(): void; onSaved(s: Site): void }) {
  const { t } = useI18n();
  const action = useAction();
  const [today] = useState(localToday);
  const [date, setDate] = useState(site.handoverDate ?? today);
  const [months, setMonths] = useState(String(site.warrantyMonths ?? 24));
  const [error, setError] = useState<string | null>(null);
  const save = async (e: FormEvent, clear = false) => {
    e.preventDefault();
    const m = Number(months);
    if (!clear && (!date || date > today)) { setError(t('site.err.handoverDate')); return; }
    if (!clear && !(Number.isInteger(m) && m >= 0 && m <= 120)) { setError(t('site.err.warranty')); return; }
    const n = await action.run(() => api.setHandover(site.id, clear ? null : date, clear ? null : m));
    if (n) onSaved(n);
  };
  return (
    <Drawer title={t('site.markHandover')} onClose={onClose}>
      <form onSubmit={save} noValidate>
        <p className="lead">{t('site.handoverLead')}</p>
        <div className="grid2">
          <div className="field">
            <label htmlFor="h-date">{t('site.handoverDate')}</label>
            <input id="h-date" type="date" className="input" value={date} max={today} onChange={(e) => { setDate(e.target.value); setError(null); }} />
          </div>
          <div className="field">
            <label htmlFor="h-months">{t('site.warrantyMonths')}</label>
            <input id="h-months" className="input" inputMode="numeric" value={months} onChange={(e) => { setMonths(e.target.value); setError(null); }} />
          </div>
        </div>
        {(error || action.error) && <p className="note bad" role="alert">{error ?? action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{t('common.save')}</button>
          {site.handoverDate && <button type="button" className="btn" onClick={(e) => save(e, true)}>{t('site.clearHandover')}</button>}
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}

function SingleSiteMap({ site }: { site: Site }) {
  const { id, latitude, longitude, name, status, lateTasks } = site;
  const pins = useMemo(() => [{ id, lat: latitude!, lng: longitude!, title: name, tone: siteTone({ status, lateTasks }) }],
    [id, latitude, longitude, name, status, lateTasks]);
  return <SitesMap pins={pins} />;
}

function SiteForm({ site, onClose, onSaved }: { site: Site; onClose(): void; onSaved(s: Site): void }) {
  const { t } = useI18n();
  const action = useAction();
  const [f, setF] = useState<SiteDetails>({
    name: site.name, address: site.address, city: site.city, country: site.country, latitude: site.latitude,
    longitude: site.longitude, projectManager: site.projectManager, foreman: site.foreman,
    plannedStart: site.plannedStart, plannedEnd: site.plannedEnd, status: site.status, notes: site.notes,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = <K extends keyof SiteDetails>(k: K, v: SiteDetails[K]) => {
    setF((x) => ({ ...x, [k]: v }));
    setErrors((e) => ({ ...e, [k]: '' }));
  };
  const geo = useCallback((r: GeoResult) => setF((x) => ({
    ...x, latitude: r.lat, longitude: r.lng, city: r.city ?? x.city, country: r.country ?? x.country,
  })), []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!f.name.trim()) errs.name = t('site.err.name');
    if (f.country && !/^[A-Za-z]{2}$/.test(f.country)) errs.country = t('deal.form.countryFormat');
    if (f.plannedStart && f.plannedEnd && f.plannedEnd < f.plannedStart) errs.plannedEnd = t('site.err.dates');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const n = await action.run(() => api.editSite(site.id, { ...f, country: f.country?.toUpperCase() || null }));
    if (n) onSaved(n);
  };
  const text = (k: 'name' | 'address' | 'city' | 'country' | 'projectManager' | 'foreman', label: string, extra?: object) => (
    <div className="field">
      <label htmlFor={`s-${k}`}>{label}</label>
      <input id={`s-${k}`} className="input" value={f[k] ?? ''} aria-invalid={!!errors[k]} {...extra}
        onChange={(e) => set(k, k === 'country' ? e.target.value.toUpperCase() : e.target.value)} />
      {errors[k] && <span className="err">{errors[k]}</span>}
    </div>
  );
  const query = [f.address, f.city, f.country].filter(Boolean).join(', ');

  return (
    <Drawer title={t('site.edit')} onClose={onClose} wide>
      <form onSubmit={submit} noValidate>
        {text('name', t('site.name'))}
        {text('address', t('site.street'))}
        <div className="grid2">
          {text('city', t('site.city'))}
          {text('country', t('site.country'), { maxLength: 2, placeholder: 'CZ' })}
        </div>
        <PinPicker value={f.latitude != null && f.longitude != null ? { lat: f.latitude, lng: f.longitude } : null}
          query={query} onChange={geo} />
        <p className="sub coords">{f.latitude != null ? `${f.latitude}, ${f.longitude}` : t('site.noPin')}
          {f.latitude != null && <> <button type="button" className="linkish" onClick={() => { set('latitude', null); set('longitude', null); }}>{t('site.clearPin')}</button></>}
        </p>
        <div className="grid2">
          {text('projectManager', t('site.pm'))}
          {text('foreman', t('site.foreman'))}
          <div className="field">
            <label htmlFor="s-start">{t('site.start')}</label>
            <input id="s-start" type="date" className="input" value={f.plannedStart ?? ''}
              onChange={(e) => set('plannedStart', e.target.value || null)} />
          </div>
          <div className="field">
            <label htmlFor="s-end">{t('site.end')}</label>
            <input id="s-end" type="date" className="input" value={f.plannedEnd ?? ''} aria-invalid={!!errors.plannedEnd}
              onChange={(e) => set('plannedEnd', e.target.value || null)} />
            {errors.plannedEnd && <span className="err">{errors.plannedEnd}</span>}
          </div>
        </div>
        <div className="field">
          <label htmlFor="s-status">{t('deal.col.status')}</label>
          <select id="s-status" className="select" value={f.status} onChange={(e) => set('status', e.target.value as SiteStatus)}>
            {SITE_STATUSES.map((x) => <option key={x} value={x}>{t(`site.status.${x}`)}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="s-notes">{t('site.notes')}</label>
          <textarea id="s-notes" className="input" rows={3} value={f.notes ?? ''} onChange={(e) => set('notes', e.target.value || null)} />
        </div>
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{t('common.save')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}

function TaskForm({ site, task, onClose, onSaved }: { site: Site; task?: SiteTask; onClose(): void; onSaved(s: Site): void }) {
  const { t } = useI18n();
  const action = useAction();
  const workTypes = useWorkTypes();
  const suppliers = useLoad(async () => (await api.listCounterparties()).filter((c) => c.roles.some((r) => r !== 'CUSTOMER')), []);
  const [f, setF] = useState({
    title: task?.title ?? '', workType: task?.workType ?? '', subcontractorId: task?.subcontractor?.id ?? '',
    start: task?.start ?? site.plannedStart ?? '', end: task?.end ?? site.plannedEnd ?? '',
    progress: String(task?.progressPct ?? 0),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = (k: keyof typeof f, v: string) => { setF((x) => ({ ...x, [k]: v })); setErrors((e) => ({ ...e, [k]: '' })); };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const progress = parseNum(f.progress);
    const errs: Record<string, string> = {};
    if (!f.title.trim()) errs.title = t('sched.err.title');
    if (!f.start) errs.start = t('sched.err.start');
    if (!f.end || (f.start && f.end < f.start)) errs.end = t('site.err.dates');
    if (!(Number.isInteger(progress) && progress >= 0 && progress <= 100)) errs.progress = t('sched.err.progress');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const data: TaskData = { title: f.title.trim(), workType: f.workType || null, subcontractorId: f.subcontractorId || null,
      start: f.start, end: f.end, progressPct: progress };
    const n = await action.run(() => (task ? api.updateTask(site.id, task.id, data) : api.addTask(site.id, data)));
    if (n) onSaved(n);
  };
  const err = (k: string) => errors[k] ? <span className="err">{errors[k]}</span> : null;

  return (
    <Drawer title={task ? t('sched.edit') : t('sched.add')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="t-title">{t('sched.task')}</label>
          <input id="t-title" className="input" value={f.title} aria-invalid={!!errors.title} onChange={(e) => set('title', e.target.value)} />
          {err('title')}
        </div>
        <div className="grid2">
          <div className="field">
            <label htmlFor="t-wt">{t('line.workType')}</label>
            <select id="t-wt" className="select" value={f.workType} onChange={(e) => set('workType', e.target.value)}>
              <option value="">—</option>
              {workTypes.list.map((w) => <option key={w.code} value={w.code}>{workTypes.name(w.code)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="t-progress">{t('sched.progressPct')}</label>
            <input id="t-progress" className="input" inputMode="numeric" value={f.progress} aria-invalid={!!errors.progress}
              onChange={(e) => set('progress', e.target.value)} />
            {err('progress')}
          </div>
          <div className="field">
            <label htmlFor="t-start">{t('site.start')}</label>
            <input id="t-start" type="date" className="input" value={f.start} aria-invalid={!!errors.start}
              onChange={(e) => set('start', e.target.value)} />
            {err('start')}
          </div>
          <div className="field">
            <label htmlFor="t-end">{t('site.end')}</label>
            <input id="t-end" type="date" className="input" value={f.end} aria-invalid={!!errors.end}
              onChange={(e) => set('end', e.target.value)} />
            {err('end')}
          </div>
        </div>
        <div className="field">
          <label htmlFor="t-sub">{t('line.subcontractor')}</label>
          <select id="t-sub" className="select" value={f.subcontractorId} onChange={(e) => set('subcontractorId', e.target.value)}>
            <option value="">{t('line.noSubcontractor')}</option>
            {suppliers.data?.map((c) => <option key={c.id} value={c.id}>{c.legalName}{c.checkStatus === 'RED' ? ` — ${t('status.RED')}` : ''}</option>)}
          </select>
        </div>
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{task ? t('common.save') : t('sched.add')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
