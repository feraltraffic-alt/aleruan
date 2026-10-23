import { useState, type FormEvent } from 'react';
import { api, type Site, type Stage, type StageDecision } from '../api';
import { Drawer } from '../components/Drawer';
import { LoadError } from '../components/LoadError';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { parseNum } from '../numbers';

async function openProtocol(id: string) {
  const tab = window.open('', '_blank');
  const url = URL.createObjectURL(await api.protocolFile(id));
  if (tab) tab.location.href = url; else window.location.href = url;
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Staged handover of the site: what each stage is worth, its status and the protocol to sign. */
export function StagesSection({ site }: { site: Site }) {
  const { t, money, day, num } = useI18n();
  const data = useLoad(() => api.stages(site.id), [site.id]);
  const action = useAction();
  const [editing, setEditing] = useState<Stage | 'new' | null>(null);
  const [deciding, setDeciding] = useState<Stage | null>(null);

  const run = async (fn: () => Promise<unknown>) => { if (await action.run(fn) !== undefined) await data.reload(); };
  const cov = data.data?.coverage;
  const cur = cov?.currency ?? undefined;
  const pct = (v: number) => (cov && cov.contractValue > 0 ? Math.min(100, Math.max(0, (v / cov.contractValue) * 100)) : 0);

  return (
    <section className="section">
      <div className="section-head">
        <div>
          <h2>{t('stage.title')}</h2>
          <p className="lead">{t('stage.lead')}</p>
        </div>
        <button className="btn" onClick={() => setEditing('new')} disabled={!data.data}>{t('stage.add')}</button>
      </div>
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      {data.error ? <LoadError message={data.error} onRetry={data.reload} /> : !data.data ? null : (
        <div className="panel">
          {cov && (
            <div className="coverage">
              <div className="coverage-bar" role="img" aria-label={t('stage.coverageBar', {
                accepted: num(pct(cov.accepted), 0), planned: num(pct(cov.planned), 0) })}>
                <span className="acc" style={{ width: `${pct(cov.accepted)}%` }} />
                <span className="plan" style={{ width: `${Math.max(0, pct(cov.planned) - pct(cov.accepted))}%` }} />
              </div>
              <div className="coverage-legend">
                <span><i className="acc" /> {t('stage.accepted', { v: money(cov.accepted, cur) })}</span>
                <span><i className="plan" /> {t('stage.planned', { v: money(cov.planned, cur) })}</span>
                <span>{t('stage.contract', { v: money(cov.contractValue, cur) })}</span>
                {cov.unassigned !== 0 && (
                  <span className={cov.unassigned < 0 ? 'neg' : 'sub'}>
                    {cov.unassigned > 0 ? t('stage.unassigned', { v: money(cov.unassigned, cur) })
                      : t('stage.over', { v: money(-cov.unassigned, cur) })}
                  </span>
                )}
              </div>
            </div>
          )}
          {data.data.stages.length === 0 ? <p className="empty">{t('stage.empty')}</p> : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>{t('stage.name')}</th><th>{t('deal.col.status')}</th><th>{t('stage.date')}</th>
                    <th className="num">{t('stage.amount')}</th><th><span className="sr">{t('line.actions')}</span></th></tr>
                </thead>
                <tbody>
                  {data.data.stages.map((s) => (
                    <tr key={s.id}>
                      <td className="name-cell">{s.seq}. {s.title}
                        {s.description && <span className="sub">{s.description}</span>}
                        {s.taskIds.length > 0 && <span className="sub">{t('stage.tasks', { n: s.taskIds.length })}</span>}
                      </td>
                      <td>
                        <span className={`pill st-${s.status}`}>{t(`stage.status.${s.status}`)}</span>
                        {s.decidedBy && <span className="sub">{s.decidedBy}, {day(s.decidedAt)}</span>}
                        {s.remarks && <span className="sub">«{s.remarks}»</span>}
                        {s.fixUntil && <span className="sub">{t('stage.fixUntil', { date: day(s.fixUntil) })}</span>}
                      </td>
                      <td>{s.plannedDate ? day(s.plannedDate) : '—'}</td>
                      <td className="num">{money(s.amount, cur)}</td>
                      <td><span className="row-actions">
                        {(s.status === 'PLANNED' || s.status === 'REJECTED') && <button className="linkish" onClick={() => setEditing(s)}>{t('line.editShort')}</button>}
                        {s.nextStatuses.includes('SUBMITTED') && <button className="linkish" onClick={() => run(() => api.stageAction(s.id, 'submit'))}>{t('stage.submit')}</button>}
                        {s.nextStatuses.includes('ACCEPTED') && <button className="linkish" onClick={() => setDeciding(s)}>{t('stage.decide')}</button>}
                        {s.status === 'SUBMITTED' && <button className="linkish" onClick={() => run(() => api.stageAction(s.id, 'withdraw'))}>{t('stage.withdraw')}</button>}
                        {s.status !== 'PLANNED' && <button className="linkish" onClick={() => openProtocol(s.id).catch((e) => action.setError(String(e)))}>{t('stage.protocol')}</button>}
                        {s.status === 'PLANNED' && <button className="linkish danger" aria-label={t('line.remove', { what: s.title })}
                          onClick={() => run(() => api.removeStage(s.id).then(() => true))}>{t('line.removeShort')}</button>}
                      </span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
      {editing && <StageForm site={site} stage={editing === 'new' ? undefined : editing} contract={cov?.contractValue ?? 0}
        currency={cur} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void data.reload(); }} />}
      {deciding && <DecisionForm stage={deciding} onClose={() => setDeciding(null)}
        onSaved={() => { setDeciding(null); void data.reload(); }} />}
    </section>
  );
}

function StageForm({ site, stage, contract, currency, onClose, onSaved }: {
  site: Site; stage?: Stage; contract: number; currency?: string; onClose(): void; onSaved(): void;
}) {
  const { t, money } = useI18n();
  const action = useAction();
  const [title, setTitle] = useState(stage?.title ?? '');
  const [description, setDescription] = useState(stage?.description ?? '');
  const [amount, setAmount] = useState(stage ? String(stage.amount) : '');
  const [date, setDate] = useState(stage?.plannedDate ?? '');
  const [tasks, setTasks] = useState<string[]>(stage?.taskIds ?? []);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const value = parseNum(amount);
  const share = (p: number) => setAmount(String(Math.round(contract * p) / 100));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!title.trim()) errs.title = t('stage.err.title');
    if (!(value >= 0)) errs.amount = t('stage.err.amount');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const data = { title: title.trim(), description: description.trim() || null, amount: value, plannedDate: date || null, taskIds: tasks };
    const r = await action.run(() => (stage ? api.editStage(stage.id, data) : api.createStage(site.id, data)));
    if (r) onSaved();
  };

  return (
    <Drawer title={stage ? t('stage.edit') : t('stage.add')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="st-title">{t('stage.name')}</label>
          <input id="st-title" className="input" value={title} aria-invalid={!!errors.title} placeholder={t('stage.nameHint')}
            onChange={(e) => { setTitle(e.target.value); setErrors({}); }} />
          {errors.title && <span className="err">{errors.title}</span>}
        </div>
        <div className="field">
          <label htmlFor="st-desc">{t('stage.description')}</label>
          <textarea id="st-desc" className="input" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="grid2">
          <div className="field">
            <label htmlFor="st-amount">{t('stage.amountLabel', { cur: currency ?? '' })}</label>
            <input id="st-amount" className="input" inputMode="decimal" value={amount} aria-invalid={!!errors.amount}
              onChange={(e) => { setAmount(e.target.value); setErrors({}); }} />
            {errors.amount && <span className="err">{errors.amount}</span>}
          </div>
          <div className="field">
            <label htmlFor="st-date">{t('stage.date')}</label>
            <input id="st-date" type="date" className="input" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
        </div>
        {contract > 0 && (
          <p className="share">{t('stage.share')}{' '}
            {[10, 25, 30, 50, 100].map((p) => <button key={p} type="button" className="linkish" onClick={() => share(p)}>{p} %</button>)}
            <span className="sub">{t('stage.contract', { v: money(contract, currency) })}</span>
          </p>
        )}
        {site.tasks.length > 0 && (
          <fieldset className="field">
            <legend>{t('stage.whichTasks')}</legend>
            <div className="picklist">
              {site.tasks.map((x) => (
                <label key={x.id}>
                  <input type="checkbox" checked={tasks.includes(x.id)}
                    onChange={() => setTasks((s) => (s.includes(x.id) ? s.filter((i) => i !== x.id) : [...s, x.id]))} />
                  <span>{x.title}<span className="sub">{x.subcontractor?.legalName ?? ''} {x.progressPct} %</span></span>
                </label>
              ))}
            </div>
          </fieldset>
        )}
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{stage ? t('common.save') : t('stage.add')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}

function DecisionForm({ stage, onClose, onSaved }: { stage: Stage; onClose(): void; onSaved(): void }) {
  const { t } = useI18n();
  const action = useAction();
  const [decision, setDecision] = useState<StageDecision['decision']>('ACCEPTED');
  const [by, setBy] = useState('');
  const [remarks, setRemarks] = useState('');
  const [fixUntil, setFixUntil] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!by.trim()) errs.by = t('co.err.by');
    if (decision !== 'ACCEPTED' && !remarks.trim()) errs.remarks = t('stage.err.remarks');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const r = await action.run(() => api.decideStage(stage.id, { decision, by: by.trim(), remarks: remarks.trim() || null,
      fixUntil: decision === 'ACCEPTED_WITH_REMARKS' && fixUntil ? fixUntil : null }));
    if (r) onSaved();
  };
  return (
    <Drawer title={t('stage.decide')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <p className="lead">{stage.seq}. {stage.title}</p>
        <fieldset className="field">
          <legend>{t('stage.decision')}</legend>
          <div className="picklist">
            {(['ACCEPTED', 'ACCEPTED_WITH_REMARKS', 'REJECTED'] as const).map((d) => (
              <label key={d}>
                <input type="radio" name="decision" checked={decision === d} onChange={() => { setDecision(d); setErrors({}); }} />
                <span>{t(`stage.status.${d}`)}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="field">
          <label htmlFor="st-by">{t('co.by')}</label>
          <input id="st-by" className="input" value={by} aria-invalid={!!errors.by} placeholder={t('co.byHint')}
            onChange={(e) => { setBy(e.target.value); setErrors({}); }} />
          {errors.by && <span className="err">{errors.by}</span>}
        </div>
        {decision !== 'ACCEPTED' && (
          <div className="field">
            <label htmlFor="st-remarks">{decision === 'REJECTED' ? t('stage.reasons') : t('stage.remarks')}</label>
            <textarea id="st-remarks" className="input" rows={3} value={remarks} aria-invalid={!!errors.remarks}
              onChange={(e) => { setRemarks(e.target.value); setErrors({}); }} />
            {errors.remarks && <span className="err">{errors.remarks}</span>}
          </div>
        )}
        {decision === 'ACCEPTED_WITH_REMARKS' && (
          <div className="field narrow">
            <label htmlFor="st-fix">{t('stage.fixUntilLabel')}</label>
            <input id="st-fix" type="date" className="input" value={fixUntil} onChange={(e) => setFixUntil(e.target.value)} />
          </div>
        )}
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{t('stage.record')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
