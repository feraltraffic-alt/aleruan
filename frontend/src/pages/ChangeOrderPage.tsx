import { useState, type FormEvent } from 'react';
import { can } from '../permissions';
import { Link, useParams } from 'react-router-dom';
import { api, type ChangeOrder, type DealLine } from '../api';
import { Drawer } from '../components/Drawer';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useWorkTypes } from '../components/dealData';
import { useI18n } from '../i18n';
import { ChangeOrderForm, ChangeOrderStatusTag } from './ChangeOrders';
import { LineForm } from './LineForm';

async function openSheet(id: string) {
  const tab = window.open('', '_blank');
  const url = URL.createObjectURL(await api.changeOrderFile(id));
  if (tab) tab.location.href = url; else window.location.href = url;
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export function ChangeOrderPage() {
  const { id = '' } = useParams();
  const { t, money, num, date } = useI18n();
  const co = useLoad(() => api.getChangeOrder(id), [id]);
  const deal = useLoad(async () => (co.data ? api.getDeal(co.data.dealId) : null), [co.data?.dealId]);
  const workTypes = useWorkTypes();
  const action = useAction();
  const [line, setLine] = useState<DealLine | 'new' | null>(null);
  const [heading, setHeading] = useState(false);
  const [deciding, setDeciding] = useState<'approve' | 'reject' | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);

  if (co.error) return <LoadError message={co.error} onRetry={co.reload} />;
  const c = co.data;
  const d = deal.data;
  if (!c || !d) return <p className="empty" aria-busy="true">{t('loading')}</p>;

  const apply = (next: ChangeOrder | undefined) => { if (next) co.setData(next); };
  const run = async (a: 'send' | 'revise' | 'cancel') => {
    if (a === 'cancel' && !confirmCancel) { setConfirmCancel(true); return; }
    setConfirmCancel(false);
    apply(await action.run(() => api.changeOrderAction(c.id, a)));
  };
  const costsOpen = (['DRAFT', 'SENT', 'APPROVED'].includes(c.status)) && can('changes.write');

  const canWrite = can('changes.write');
  return (
    <>
      <Link to={`/deals/${c.dealId}`} className="crumb">{t('rfq.back', { deal: d.number })}</Link>
      <div className="page-head">
        <div>
          <h1>{c.title}</h1>
          <p className="lead">{c.number} · <ChangeOrderStatusTag status={c.status} /></p>
        </div>
        <div className="moves">
          {c.lines.length > 0 && <button className="btn" onClick={() => openSheet(c.id).catch((e) => action.setError(String(e)))}>{t('co.sheet')}</button>}
          {canWrite && c.nextStatuses.includes('SENT') && <button className="btn primary" disabled={action.busy} onClick={() => run('send')}>{t('co.send')}</button>}
          {canWrite && c.nextStatuses.includes('APPROVED') && <button className="btn primary" onClick={() => setDeciding('approve')}>{t('co.approve')}</button>}
          {canWrite && c.nextStatuses.includes('REJECTED') && <button className="btn" onClick={() => setDeciding('reject')}>{t('co.reject')}</button>}
          {canWrite && c.nextStatuses.includes('DRAFT') && <button className="btn" disabled={action.busy} onClick={() => run('revise')}>{t('co.revise')}</button>}
          {canWrite && c.nextStatuses.includes('CANCELLED') && (
            <button className="btn" disabled={action.busy} onClick={() => run('cancel')}>
              {confirmCancel ? t('deal.confirm', { what: t('co.cancel') }) : t('co.cancel')}
            </button>
          )}
        </div>
      </div>
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      {c.status === 'SENT' && <p className="note">{t('co.sentNote')}</p>}
      {c.status === 'APPROVED' && <p className="note ok">{t('co.approvedNote', { who: c.decidedBy ?? '', when: date(c.decidedAt) })}{c.decisionNote ? ` «${c.decisionNote}»` : ''}</p>}
      {c.status === 'REJECTED' && <p className="note bad">{t('co.rejectedNote', { who: c.decidedBy ?? '', when: date(c.decidedAt) })}{c.decisionNote ? ` «${c.decisionNote}»` : ''}</p>}

      <dl className="panel facts">
        <div><dt>{t('co.reason')}</dt><dd>{c.reason ?? '—'}</dd></div>
        <div><dt>{t('co.daysLabel')}</dt><dd>{c.extraDays ? `${c.extraDays > 0 ? '+' : ''}${c.extraDays}` : '—'}</dd></div>
        <div><dt>{t('totals.price')}</dt><dd>{money(c.totals.price, d.currency)}</dd></div>
        <div><dt>{t('totals.margin')}</dt><dd className={c.totals.margin < 0 ? 'neg' : ''}>{money(c.totals.margin, d.currency)}
          {c.totals.marginPct != null && <span className="sub">{num(c.totals.marginPct, 1)} %</span>}</dd></div>
        {canWrite && c.pricesEditable && <div><dt>&nbsp;</dt><dd><button className="linkish" onClick={() => setHeading(true)}>{t('co.edit')}</button></dd></div>}
      </dl>

      <section className="section">
        <div className="section-head">
          <h2>{t('line.title')}</h2>
          {canWrite && c.pricesEditable && <button className="btn" onClick={() => setLine('new')}>{t('line.add')}</button>}
        </div>
        {!c.pricesEditable && costsOpen && <p className="note">{t('co.frozen')}</p>}
        <div className="panel table-wrap">
          {c.lines.length === 0 ? <p className="empty">{t('co.noLines')}</p> : (
            <table className="lines">
              <thead>
                <tr>
                  <th>{t('line.description')}</th><th className="num">{t('line.quantity')}</th>
                  <th className="num">{t('line.unitCostShort')}</th><th className="num">{t('line.unitPrice')}</th>
                  <th className="num">{t('line.priceTotal')}</th><th className="num">{t('totals.margin')}</th>
                  <th>{t('line.subcontractor')}</th>{costsOpen && <th><span className="sr">{t('line.actions')}</span></th>}
                </tr>
              </thead>
              <tbody>
                {c.lines.map((l) => (
                  <tr key={l.id}>
                    <td className="name-cell">{l.description}
                      <span className="sub">{t(`kind.${l.kind}`)}{l.workType ? `, ${workTypes.name(l.workType)}` : ''}</span></td>
                    <td className={`num${l.quantity < 0 ? ' neg' : ''}`}>{num(l.quantity, 4)} {l.unit}</td>
                    <td className="num">{money(l.unitCost)}</td>
                    <td className="num">{money(l.unitPrice)}</td>
                    <td className="num">{money(l.priceTotal)}</td>
                    <td className={`num${l.margin < 0 ? ' neg' : ''}`}>{money(l.margin)}</td>
                    <td>{l.subcontractor ? <>{l.subcontractor.legalName}<span className="sub"><Status status={l.subcontractor.checkStatus} /></span></>
                      : <span className="sub">{t('line.noSubcontractor')}</span>}</td>
                    {costsOpen && (
                      <td><span className="row-actions">
                        <button className="linkish" onClick={() => setLine(l)}>{c.pricesEditable ? t('line.editShort') : t('co.editCost')}</button>
                        {c.pricesEditable && (
                          <button className="linkish danger" aria-label={t('line.remove', { what: l.description })}
                            onClick={async () => apply(await action.run(() => api.removeChangeLine(c.id, l.id)))}>
                            {t('line.removeShort')}
                          </button>
                        )}
                      </span></td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      {line && (
        <LineForm pricing={d} line={line === 'new' ? undefined : line} allowNegative onClose={() => setLine(null)}
          save={(data) => (line === 'new' ? api.addChangeLine(c.id, data) : api.updateChangeLine(c.id, line.id, data))}
          onSaved={(next) => { co.setData(next); setLine(null); }} />
      )}
      {heading && <ChangeOrderForm existing={c} onClose={() => setHeading(false)}
        onSaved={() => { setHeading(false); void co.reload(); }} />}
      {deciding && <DecisionForm co={c} decision={deciding} onClose={() => setDeciding(null)}
        onDone={(next) => { co.setData(next); setDeciding(null); }} />}
    </>
  );
}

function DecisionForm({ co, decision, onClose, onDone }: {
  co: ChangeOrder; decision: 'approve' | 'reject'; onClose(): void; onDone(c: ChangeOrder): void;
}) {
  const { t } = useI18n();
  const action = useAction();
  const [by, setBy] = useState('');
  const [note, setNote] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!by.trim()) { setErr(t('co.err.by')); return; }
    const next = await action.run(() => api.decideChangeOrder(co.id, decision, by.trim(), note.trim() || null));
    if (next) onDone(next);
  };
  return (
    <Drawer title={decision === 'approve' ? t('co.approve') : t('co.reject')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <p className="lead">{decision === 'approve' ? t('co.approveLead') : t('co.rejectLead')}</p>
        <div className="field">
          <label htmlFor="co-by">{t('co.by')}</label>
          <input id="co-by" className="input" value={by} aria-invalid={!!err} placeholder={t('co.byHint')}
            onChange={(e) => { setBy(e.target.value); setErr(null); }} />
          {err && <span className="err">{err}</span>}
        </div>
        <div className="field">
          <label htmlFor="co-note">{t('co.note')}</label>
          <textarea id="co-note" className="input" rows={3} value={note} placeholder={t('co.noteHint')}
            onChange={(e) => setNote(e.target.value)} />
        </div>
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{decision === 'approve' ? t('co.approveNow') : t('co.rejectNow')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
