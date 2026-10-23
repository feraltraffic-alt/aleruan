import { useState, type FormEvent } from 'react';
import { can } from '../permissions';
import { Link, useNavigate } from 'react-router-dom';
import { api, type ChangeOrderStatus, type Deal } from '../api';
import { Drawer } from '../components/Drawer';
import { LoadError } from '../components/LoadError';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { parseNum } from '../numbers';

export function ChangeOrderStatusTag({ status }: { status: ChangeOrderStatus }) {
  const { t } = useI18n();
  return <span className={`pill c-${status}`}>{t(`co.status.${status}`)}</span>;
}

/** Change orders of a deal, shown once the customer accepted (or when some exist). */
export function ChangeOrdersSection({ deal }: { deal: Deal }) {
  const { t, money, day } = useI18n();
  const list = useLoad(() => api.changeOrders(deal.id), [deal.id]);
  const [creating, setCreating] = useState(false);
  const alive = deal.status === 'WON' || deal.status === 'IN_PROGRESS';
  if (!alive && !list.data?.length) return null;

  return (
    <section className="section">
      <div className="section-head">
        <div>
          <h2>{t('co.title')}</h2>
          <p className="lead">{t('co.lead')}</p>
        </div>
        {alive && can('changes.write') && <button className="btn" onClick={() => setCreating(true)}>{t('co.add')}</button>}
      </div>
      {list.error ? <LoadError message={list.error} onRetry={list.reload} /> : !list.data ? null : (
        <div className="panel table-wrap">
          {list.data?.length === 0 ? <p className="empty">{t('co.empty')}</p> : (
            <table>
              <thead>
                <tr><th>{t('quote.col.number')}</th><th>{t('deal.col.status')}</th><th>{t('co.days')}</th>
                  <th className="num">{t('totals.price')}</th><th className="num">{t('totals.margin')}</th></tr>
              </thead>
              <tbody>
                {list.data?.map((c) => (
                  <tr key={c.id}>
                    <td className="name-cell"><Link to={`/change-orders/${c.id}`}>{c.number}</Link><span className="sub">{c.title}</span></td>
                    <td><ChangeOrderStatusTag status={c.status} />
                      {c.decidedBy && <span className="sub">{c.decidedBy}, {day(c.decidedAt)}</span>}</td>
                    <td>{c.extraDays ? `${c.extraDays > 0 ? '+' : ''}${c.extraDays}` : '—'}</td>
                    <td className="num">{money(c.totals.price, deal.currency)}</td>
                    <td className={`num${c.totals.margin < 0 ? ' neg' : ''}`}>{money(c.totals.margin, deal.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      {creating && <ChangeOrderForm dealId={deal.id} onClose={() => setCreating(false)} />}
    </section>
  );
}

/** Creates a change order (or edits its heading when `existing` is given). */
export function ChangeOrderForm({ dealId, existing, onClose, onSaved }: {
  dealId?: string; existing?: { id: string; title: string; reason: string | null; extraDays: number };
  onClose(): void; onSaved?(): void;
}) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const action = useAction();
  const [title, setTitle] = useState(existing?.title ?? '');
  const [reason, setReason] = useState(existing?.reason ?? '');
  const [days, setDays] = useState(String(existing?.extraDays ?? 0));
  const [errors, setErrors] = useState<Record<string, string>>({});

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const n = parseNum(days || '0');
    const errs: Record<string, string> = {};
    if (!title.trim()) errs.title = t('co.err.title');
    if (!Number.isInteger(n)) errs.days = t('co.err.days');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const draft = { title: title.trim(), reason: reason.trim() || null, extraDays: n };
    const c = await action.run(() => (existing ? api.editChangeOrder(existing.id, draft) : api.createChangeOrder(dealId!, draft)));
    if (!c) return;
    if (existing) onSaved?.(); else navigate(`/change-orders/${c.id}`);
  };

  return (
    <Drawer title={existing ? t('co.edit') : t('co.add')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="co-title">{t('co.name')}</label>
          <input id="co-title" className="input" value={title} aria-invalid={!!errors.title}
            placeholder={t('co.nameHint')} onChange={(e) => { setTitle(e.target.value); setErrors({}); }} />
          {errors.title && <span className="err">{errors.title}</span>}
        </div>
        <div className="field">
          <label htmlFor="co-reason">{t('co.reason')}</label>
          <textarea id="co-reason" className="input" rows={3} value={reason} placeholder={t('co.reasonHint')}
            onChange={(e) => setReason(e.target.value)} />
        </div>
        <div className="field narrow">
          <label htmlFor="co-days">{t('co.daysLabel')}</label>
          <input id="co-days" className="input" inputMode="numeric" value={days} aria-invalid={!!errors.days}
            onChange={(e) => { setDays(e.target.value); setErrors({}); }} />
          {errors.days && <span className="err">{errors.days}</span>}
        </div>
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{existing ? t('common.save') : t('co.create')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
