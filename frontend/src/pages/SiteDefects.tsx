import { useState, type FormEvent } from 'react';
import { can } from '../permissions';
import { Link } from 'react-router-dom';
import { api, type Defect, type DefectData, type DefectSeverity, type DefectStatus, type Site } from '../api';
import { Drawer } from '../components/Drawer';
import { LoadError } from '../components/LoadError';
import { Photos } from '../components/Photos';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { localToday } from '../dates';
import { useI18n, type MessageKey } from '../i18n';

const SEVERITIES: DefectSeverity[] = ['MINOR', 'MAJOR', 'CRITICAL'];
/** Button labels per move, worded by who does it: the subcontractor fixes, Aleruan checks. */
const MOVE: Record<string, MessageKey> = {
  'OPEN>FIXED': 'defect.to.fixed', 'OPEN>DISMISSED': 'defect.to.dismissed', 'FIXED>CLOSED': 'defect.to.closed',
  'FIXED>OPEN': 'defect.to.reopen', 'CLOSED>OPEN': 'defect.to.reopen', 'DISMISSED>OPEN': 'defect.to.reopen',
};
/** Moves that need a written reason. */
const needsNote = (from: DefectStatus, to: DefectStatus) => to === 'DISMISSED' || (from === 'FIXED' && to === 'OPEN');

/** One defect card with its status actions and photos; used on the site page and in the overview. */
export function DefectCard({ defect, onChange, showSite, onEdit }: {
  defect: Defect; onChange(d: Defect): void; showSite?: boolean; onEdit?(d: Defect): void;
}) {
  const { t, day } = useI18n();
  const action = useAction();
  const [pending, setPending] = useState<DefectStatus | null>(null);
  const [note, setNote] = useState('');
  const d = defect;

  const move = async (to: DefectStatus) => {
    if ((needsNote(d.status, to) || to === 'FIXED') && pending !== to) { setPending(to); setNote(''); return; }
    if (needsNote(d.status, to) && !note.trim()) { action.setError(t('defect.err.reason')); return; }
    const next = await action.run(() => api.moveDefect(d.id, to, note.trim() || null));
    if (next) { setPending(null); onChange(next); }
  };

  return (
    <article className={`panel defect sev-${d.severity}${d.status === 'CLOSED' || d.status === 'DISMISSED' ? ' done' : ''}`}>
      <header>
        <span className="defect-no">#{d.seq}</span>
        <div className="defect-head">
          <b>{d.title}</b>
          <span className="sub">
            {showSite && <><Link to={`/sites/${d.siteId}`}>{d.siteName}</Link>{d.location ? ', ' : ''}</>}
            {d.location}
          </span>
        </div>
        <span className={`pill df-${d.status}`}>{t(`defect.status.${d.status}`)}</span>
      </header>
      <div className="defect-tags">
        <span className={`tag sev ${d.severity}`}>{t(`defect.sev.${d.severity}`)}</span>
        {d.warranty && <span className="tag warranty">{t('defect.warranty')}</span>}
        <span className="sub">{t('defect.found', { date: day(d.foundOn) })}</span>
        {d.dueDate && d.status === 'OPEN' && (
          <span className={`sub${d.overdue ? ' neg' : ''}`}>{d.overdue ? t('defect.overdue', { date: day(d.dueDate) }) : t('defect.due', { date: day(d.dueDate) })}</span>
        )}
        <span className="sub">{d.subcontractor ? t('defect.who', { who: d.subcontractor.legalName }) : t('defect.noOne')}</span>
      </div>
      {d.description && <p className="defect-text">{d.description}</p>}
      {d.fixNote && <p className="sub">{d.status === 'DISMISSED' ? t('defect.dismissedWhy') : d.status === 'OPEN' ? t('defect.reopenedWhy') : t('defect.fixNote')}: {d.fixNote}</p>}
      <Photos owner="defects" ownerId={d.id} photos={d.photos} editable={d.status !== 'CLOSED' && can('site.records')}
        onChange={(photos) => onChange({ ...d, photos })} />
      {pending && (
        <div className="field defect-note">
          <label htmlFor={`n-${d.id}`}>{needsNote(d.status, pending) ? t('defect.reason') : t('defect.fixNoteLabel')}</label>
          <input id={`n-${d.id}`} className="input" value={note} onChange={(e) => setNote(e.target.value)} autoFocus />
        </div>
      )}
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      <div className="moves">
        {can('site.records') && d.nextStatuses.map((s) => (
          <button key={s} className={`btn small${s === 'CLOSED' || s === 'FIXED' ? ' primary' : ''}`} disabled={action.busy}
            onClick={() => move(s)}>
            {pending === s ? t('defect.confirm', { what: t(MOVE[`${d.status}>${s}`]) }) : t(MOVE[`${d.status}>${s}`])}
          </button>
        ))}
        {pending && <button className="linkish" onClick={() => setPending(null)}>{t('form.cancel')}</button>}
        {onEdit && d.status === 'OPEN' && <button className="linkish" onClick={() => onEdit(d)}>{t('line.editShort')}</button>}
      </div>
    </article>
  );
}

export function DefectsSection({ site, onChanged }: { site: Site; onChanged(): void }) {
  const { t, day } = useI18n();
  const defects = useLoad(() => api.siteDefects(site.id), [site.id]);
  const [all, setAll] = useState(false);
  const [editing, setEditing] = useState<Defect | 'new' | null>(null);
  const shown = (defects.data ?? []).filter((d) => all || d.status === 'OPEN' || d.status === 'FIXED');
  const update = (d: Defect) => {
    defects.setData((list) => (list ?? []).map((x) => (x.id === d.id ? d : x)));
    onChanged();
  };

  return (
    <section className="section" aria-label={t('defect.title')}>
      <div className="section-head">
        <div>
          <h2>{t('defect.title')}</h2>
          <p className="lead">
            {site.warrantyUntil ? t('defect.warrantyUntil', { date: day(site.warrantyUntil) }) : t('defect.lead')}
          </p>
        </div>
        {can('site.records') && <button className="btn primary" onClick={() => setEditing('new')}>{t('defect.add')}</button>}
      </div>
      <label className="toggle">
        <input type="checkbox" checked={all} onChange={(e) => setAll(e.target.checked)} /> {t('defect.showClosed')}
      </label>
      {defects.error ? <LoadError message={defects.error} onRetry={defects.reload} /> : (
        shown.length === 0 ? <div className="panel"><p className="empty">{defects.data?.length ? t('defect.allResolved') : t('defect.empty')}</p></div> : (
          <div className="defects">
            {shown.map((d) => <DefectCard key={d.id} defect={d} onChange={update} onEdit={setEditing} />)}
          </div>
        )
      )}
      {editing && (
        <DefectForm site={site} defect={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); void defects.reload(); onChanged(); }} />
      )}
    </section>
  );
}

function DefectForm({ site, defect, onClose, onSaved }: { site: Site; defect?: Defect; onClose(): void; onSaved(): void }) {
  const { t } = useI18n();
  const action = useAction();
  const subs = useLoad(async () => (await api.listCounterparties()).filter((c) =>
    c.roles.includes('SUBCONTRACTOR') || c.roles.includes('SUPPLIER')), []);
  const stages = useLoad(() => api.stages(site.id), [site.id]);
  const [today] = useState(localToday);
  const fromTasks = [...new Set(site.tasks.map((x) => x.subcontractor?.id).filter(Boolean))] as string[];
  const [f, setF] = useState({
    title: defect?.title ?? '', description: defect?.description ?? '', location: defect?.location ?? '',
    severity: defect?.severity ?? 'MINOR' as DefectSeverity, subcontractorId: defect?.subcontractor?.id ?? '',
    stageId: defect?.stageId ?? '', foundOn: defect?.foundOn ?? today, dueDate: defect?.dueDate ?? '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = (k: keyof typeof f, v: string) => { setF((x) => ({ ...x, [k]: v })); setErrors((e) => ({ ...e, [k]: '' })); };
  const sorted = [...(subs.data ?? [])].sort((a, b) => Number(fromTasks.includes(b.id)) - Number(fromTasks.includes(a.id)));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!f.title.trim()) errs.title = t('defect.err.title');
    if (!f.foundOn) errs.foundOn = t('defect.err.found');
    if (f.dueDate && f.foundOn && f.dueDate < f.foundOn) errs.dueDate = t('defect.err.due');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const data: DefectData = { title: f.title.trim(), description: f.description.trim() || null,
      location: f.location.trim() || null, severity: f.severity, subcontractorId: f.subcontractorId || null,
      stageId: f.stageId || null, foundOn: f.foundOn, dueDate: f.dueDate || null };
    const d = await action.run(() => (defect ? api.editDefect(defect.id, data) : api.reportDefect(site.id, data)));
    if (d) onSaved();
  };
  const err = (k: string) => errors[k] ? <span className="err">{errors[k]}</span> : null;

  return (
    <Drawer title={defect ? t('defect.edit') : t('defect.add')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="df-title">{t('defect.what')}</label>
          <input id="df-title" className="input" value={f.title} aria-invalid={!!errors.title} onChange={(e) => set('title', e.target.value)} />
          {err('title')}
        </div>
        <div className="field">
          <label htmlFor="df-loc">{t('defect.location')}</label>
          <input id="df-loc" className="input" value={f.location} placeholder={t('defect.locationHint')}
            onChange={(e) => set('location', e.target.value)} />
        </div>
        <fieldset className="field">
          <legend>{t('defect.severity')}</legend>
          <div className="checks">
            {SEVERITIES.map((s) => (
              <label key={s}><input type="radio" name="sev" checked={f.severity === s} onChange={() => set('severity', s)} />
                {t(`defect.sev.${s}`)}</label>
            ))}
          </div>
          <span className="sub">{t(`defect.sevHint.${f.severity}`)}</span>
        </fieldset>
        <div className="grid2">
          <div className="field">
            <label htmlFor="df-found">{t('defect.foundOn')}</label>
            <input id="df-found" type="date" className="input" value={f.foundOn} max={today} aria-invalid={!!errors.foundOn}
              onChange={(e) => set('foundOn', e.target.value)} />
            {err('foundOn')}
          </div>
          <div className="field">
            <label htmlFor="df-due">{t('defect.dueOn')}</label>
            <input id="df-due" type="date" className="input" value={f.dueDate} aria-invalid={!!errors.dueDate}
              onChange={(e) => set('dueDate', e.target.value)} />
            {err('dueDate') ?? <span className="sub">{t('defect.dueAuto')}</span>}
          </div>
        </div>
        <div className="field">
          <label htmlFor="df-sub">{t('defect.responsible')}</label>
          <select id="df-sub" className="select" value={f.subcontractorId} onChange={(e) => set('subcontractorId', e.target.value)}>
            <option value="">{t('defect.noOne')}</option>
            {sorted.map((c) => <option key={c.id} value={c.id}>{c.legalName}{fromTasks.includes(c.id) ? ` — ${t('defect.onSite')}` : ''}</option>)}
          </select>
        </div>
        {!!stages.data?.stages.length && (
          <div className="field">
            <label htmlFor="df-stage">{t('defect.stage')}</label>
            <select id="df-stage" className="select" value={f.stageId} onChange={(e) => set('stageId', e.target.value)}>
              <option value="">—</option>
              {stages.data.stages.map((s) => <option key={s.id} value={s.id}>{s.seq}. {s.title}</option>)}
            </select>
          </div>
        )}
        <div className="field">
          <label htmlFor="df-desc">{t('defect.description')}</label>
          <textarea id="df-desc" className="input" rows={3} value={f.description} onChange={(e) => set('description', e.target.value)} />
        </div>
        {site.handoverDate && f.foundOn > site.handoverDate && <p className="note">{t('defect.willBeWarranty')}</p>}
        {!defect && <p className="sub">{t('defect.photosAfter')}</p>}
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{defect ? t('common.save') : t('defect.add')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
