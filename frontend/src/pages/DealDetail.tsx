import { useState, type FormEvent } from 'react';
import { can } from '../permissions';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, type Deal, type DealLine, type DealStatus } from '../api';
import { Drawer } from '../components/Drawer';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { DealStatusTag, RfqStatusTag, Warnings } from '../components/dealBits';
import { useWorkTypes } from '../components/dealData';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { parseNum } from '../numbers';
import { LineForm } from './LineForm';
import { QuotesSection } from './Quotes';
import { DealSite } from '../components/DealSite';
import { DealInvoicing } from '../components/DealInvoicing';
import { ChangeOrdersSection } from './ChangeOrders';

/** Moves that end the deal ask for a second click. */
const FINAL: DealStatus[] = ['LOST', 'CANCELLED'];

export function DealDetailPage() {
  const { id = '' } = useParams();
  const { t, date, day, money, num } = useI18n();
  const deal = useLoad(() => api.getDeal(id), [id]);
  const rfqs = useLoad(() => api.dealRfqs(id), [id]);
  const workTypes = useWorkTypes();
  const action = useAction();
  const [editing, setEditing] = useState<DealLine | 'new' | null>(null);
  const [tender, setTender] = useState(false);
  const [confirm, setConfirm] = useState<DealStatus | null>(null);
  const [markupDraft, setMarkupDraft] = useState<string | null>(null);

  if (deal.error) return <LoadError message={deal.error} onRetry={deal.reload} />;
  const d = deal.data;
  if (!d) return <p className="empty" aria-busy="true">{t('loading')}</p>;
  const editable = d.pricesEditable && can('deals.write');

  const apply = (next: Deal | undefined) => { if (next) deal.setData(next); };
  const move = async (s: DealStatus) => {
    if (FINAL.includes(s) && confirm !== s) { setConfirm(s); return; }
    setConfirm(null);
    apply(await action.run(() => api.moveDeal(d.id, s)));
  };
  const saveMarkup = async (e: FormEvent) => {
    e.preventDefault();
    const v = parseNum(markupDraft ?? '');
    if (!(v > -100)) { action.setError(t('deal.form.markupFormat')); return; }
    const next = await action.run(() => api.editDeal(d.id, { title: d.title, defaultMarkupPct: v, siteCountry: d.siteCountry, notes: d.notes }));
    if (next) { deal.setData(next); setMarkupDraft(null); }
  };
  const remove = async (l: DealLine) => apply(await action.run(() => api.removeLine(d.id, l.id)));

  const agreed = d.contract.total;
  const costShare = agreed.price > 0 ? Math.min(100, Math.max(0, (agreed.cost / agreed.price) * 100)) : 0;

  return (
    <>
      <Link to="/deals" className="crumb">{t('deal.back')}</Link>
      <div className="page-head">
        <div>
          <h1>{d.title}</h1>
          <p className="lead">{d.number} · <DealStatusTag status={d.status} /></p>
        </div>
        <div className="moves">
          {can('deals.write') && d.nextStatuses.map((s) => (
            <button key={s} className={`btn${s === 'QUOTED' || s === 'WON' || s === 'IN_PROGRESS' || s === 'COMPLETED' ? ' primary' : ''}`}
              disabled={action.busy} onClick={() => move(s)}>
              {confirm === s ? t('deal.confirm', { what: t(`deal.to.${s}`) }) : t(`deal.to.${s}`)}
            </button>
          ))}
        </div>
      </div>
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      <Warnings warnings={d.warnings} />
      <DealSite deal={d} />

      <section className="panel totals" aria-label={t('totals.title')}>
        <div className="totals-figures">
          <div><span>{t('totals.price')}</span><b>{money(agreed.price, d.currency)}</b></div>
          <div><span>{t('totals.cost')}</span><b>{money(agreed.cost, d.currency)}</b></div>
          <div className={agreed.margin < 0 ? 'neg' : ''}>
            <span>{t('totals.margin')}</span><b>{money(agreed.margin, d.currency)}</b>
          </div>
          <div>
            <span>{t('totals.marginPct')}</span>
            <b>{agreed.marginPct == null ? '—' : `${num(agreed.marginPct, 1)} %`}</b>
            {agreed.markupPct != null && <small>{t('totals.markupPct', { pct: num(agreed.markupPct, 1) })}</small>}
          </div>
        </div>
        {(d.contract.approvedChanges.price !== 0 || d.contract.pendingPrice !== 0) && (
          <p className="contract-line">
            {t('contract.original', { v: money(d.contract.original.price, d.currency) })}
            {d.contract.approvedChanges.price !== 0 && <> · {t('contract.approved', { v: money(d.contract.approvedChanges.price, d.currency) })}</>}
            {d.contract.pendingPrice !== 0 && <> · {t('contract.pending', { v: money(d.contract.pendingPrice, d.currency) })}</>}
            {d.contract.extraDays !== 0 && <> · {t('contract.days', { n: d.contract.extraDays })}</>}
          </p>
        )}
        {agreed.price > 0 && (
          <div className="marginbar" role="img"
            aria-label={t('totals.bar', { cost: num(costShare, 0), margin: num(100 - costShare, 0) })}>
            <span className="cost" style={{ flexBasis: `${costShare}%` }} />
            <span className="margin" />
          </div>
        )}
      </section>

      <dl className="panel facts">
        <div><dt>{t('deal.form.customer')}</dt><dd>
          {d.customer ? <Link to={`/counterparties/${d.customer.id}`}>{d.customer.legalName}</Link> : '—'}
          {d.customer && <> <Status status={d.customer.checkStatus} /></>}
        </dd></div>
        <div><dt>{t('deal.form.direction')}</dt><dd>{t(`dir.${d.direction}`)}</dd></div>
        <div><dt>{t('deal.form.siteCountry')}</dt><dd>{d.siteCountry ?? '—'}</dd></div>
        <div><dt>{t('deal.form.currency')}</dt><dd>{d.currency}</dd></div>
        <div>
          <dt>{t('deal.form.markup')}</dt>
          <dd>
            {markupDraft == null ? (
              <>
                {num(d.defaultMarkupPct)} %{' '}
                {editable && (
                  <button className="linkish" onClick={() => setMarkupDraft(String(d.defaultMarkupPct))}>
                    {t('deal.change')}
                  </button>
                )}
              </>
            ) : (
              <form className="inline-edit" onSubmit={saveMarkup}>
                <input className="input" inputMode="decimal" value={markupDraft} aria-label={t('deal.form.markup')}
                  onChange={(e) => setMarkupDraft(e.target.value)} autoFocus />
                <button className="btn" disabled={action.busy}>{t('common.save')}</button>
                <button type="button" className="linkish" onClick={() => setMarkupDraft(null)}>{t('form.cancel')}</button>
              </form>
            )}
          </dd>
        </div>
        <div><dt>{t('deal.col.created')}</dt><dd>{date(d.createdAt)}</dd></div>
      </dl>

      <section className="section">
        <div className="section-head">
          <h2>{t('line.title')}</h2>
          {editable && <button className="btn" onClick={() => setEditing('new')}>{t('line.add')}</button>}
        </div>
        {!d.pricesEditable && <p className="note">{t('deal.frozen')}</p>}
        <div className="panel table-wrap">
          {d.lines.length === 0 ? <p className="empty">{t('line.empty')}</p> : (
            <table className="lines">
              <thead>
                <tr>
                  <th>{t('line.description')}</th>
                  <th className="num">{t('line.quantity')}</th>
                  <th className="num">{t('line.unitCostShort')}</th>
                  <th className="num">{t('line.markupShort')}</th>
                  <th className="num">{t('line.unitPrice')}</th>
                  <th className="num">{t('line.priceTotal')}</th>
                  <th className="num">{t('totals.margin')}</th>
                  <th>{t('line.subcontractor')}</th>
                  {editable && <th><span className="sr">{t('line.actions')}</span></th>}
                </tr>
              </thead>
              <tbody>
                {d.lines.map((l) => (
                  <tr key={l.id}>
                    <td className="name-cell">
                      {l.description}
                      <span className="sub">{t(`kind.${l.kind}`)}{l.workType ? `, ${workTypes.name(l.workType)}` : ''}</span>
                    </td>
                    <td className="num">{num(l.quantity, 4)} {l.unit}</td>
                    <td className="num">{money(l.unitCost)}</td>
                    <td className="num">
                      {l.markupPct == null ? '—' : `${num(l.markupPct, 1)} %`}
                      <span className="sub">
                        {l.fixedUnitPrice != null ? t('line.src.fixed') : l.markupFromDeal ? t('line.src.deal') : t('line.src.own')}
                      </span>
                    </td>
                    <td className="num">{money(l.unitPrice)}</td>
                    <td className="num">{money(l.priceTotal)}</td>
                    <td className={`num${l.margin < 0 ? ' neg' : ''}`}>{money(l.margin)}</td>
                    <td>
                      {l.subcontractor ? (
                        <>
                          <Link to={`/counterparties/${l.subcontractor.id}`}>{l.subcontractor.legalName}</Link>
                          <span className="sub"><Status status={l.subcontractor.checkStatus} /></span>
                        </>
                      ) : <span className="sub">{t('line.noSubcontractor')}</span>}
                    </td>
                    {editable && (
                      <td><span className="row-actions">
                        <button className="linkish" onClick={() => setEditing(l)}>{t('line.editShort')}</button>
                        <button className="linkish danger" onClick={() => remove(l)}
                          aria-label={t('line.remove', { what: l.description })}>{t('line.removeShort')}</button>
                      </span></td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <QuotesSection deal={d} onIssued={deal.reload} />
      {can('invoices.read') && <DealInvoicing deal={d} />}

      <ChangeOrdersSection deal={d} />

      <section className="section">
        <div className="section-head">
          <div>
            <h2>{t('rfq.title')}</h2>
            <p className="lead">{t('rfq.lead')}</p>
          </div>
          {d.lines.length > 0 && !['LOST', 'COMPLETED', 'CANCELLED'].includes(d.status) && (
            can('tenders.write') && <button className="btn" onClick={() => setTender(true)}>{t('rfq.add')}</button>
          )}
        </div>
        {rfqs.error ? <LoadError message={rfqs.error} onRetry={rfqs.reload} /> : (
          <div className="panel table-wrap">
            {rfqs.data?.length === 0 ? <p className="empty">{t('rfq.empty')}</p> : (
              <table>
                <thead>
                  <tr>
                    <th>{t('rfq.col.title')}</th><th>{t('deal.col.status')}</th><th>{t('rfq.deadline')}</th>
                    <th className="num">{t('rfq.col.invited')}</th><th className="num">{t('rfq.col.offers')}</th>
                  </tr>
                </thead>
                <tbody>
                  {rfqs.data?.map((r) => (
                    <tr key={r.id}>
                      <td className="name-cell"><Link to={`/rfqs/${r.id}`}>{r.title}</Link>
                        <span className="sub">{t('rfq.items', { n: r.items.length })}</span></td>
                      <td><RfqStatusTag status={r.status} /></td>
                      <td>{r.deadline ? day(r.deadline) : '—'}</td>
                      <td className="num">{r.invitations.length}</td>
                      <td className="num">{r.invitations.filter((i) => i.offerSubmittedAt).length}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>

      {editing && (
        <LineForm pricing={d} line={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}
          save={(data) => (editing === 'new' ? api.addLine(d.id, data) : api.updateLine(d.id, editing.id, data))}
          onSaved={(next) => { deal.setData(next); setEditing(null); }} />
      )}
      {tender && <RfqForm deal={d} onClose={() => setTender(false)} />}
    </>
  );
}

function RfqForm({ deal, onClose }: { deal: Deal; onClose(): void }) {
  const { t, num } = useI18n();
  const navigate = useNavigate();
  const action = useAction();
  const withoutSub = deal.lines.filter((l) => !l.subcontractor).map((l) => l.id);
  const [picked, setPicked] = useState<string[]>(withoutSub.length ? withoutSub : deal.lines.map((l) => l.id));
  const [title, setTitle] = useState('');
  const [deadline, setDeadline] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [today] = useState(() => new Date().toISOString().slice(0, 10));

  const toggle = (id: string) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!picked.length) { setErr(t('rfq.err.lines')); return; }
    const r = await action.run(() => api.createRfq(deal.id,
      { title: title.trim() || undefined, deadline: deadline || null, lineIds: picked }));
    if (r) navigate(`/rfqs/${r.id}`);
  };

  return (
    <Drawer title={t('rfq.add')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <fieldset className="field">
          <legend>{t('rfq.pickLines')}</legend>
          <div className="picklist">
            {deal.lines.map((l) => (
              <label key={l.id}>
                <input type="checkbox" checked={picked.includes(l.id)} onChange={() => { toggle(l.id); setErr(null); }} />
                <span>{l.description}<span className="sub">{num(l.quantity, 4)} {l.unit}</span></span>
              </label>
            ))}
          </div>
          {err && <span className="err">{err}</span>}
        </fieldset>
        <div className="field">
          <label htmlFor="r-title">{t('rfq.col.title')}</label>
          <input id="r-title" className="input" value={title} placeholder={`Poptávka ${deal.number}`}
            onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="r-deadline">{t('rfq.deadline')}</label>
          <input id="r-deadline" type="date" className="input" value={deadline}
            min={today} onChange={(e) => setDeadline(e.target.value)} />
        </div>
        <p className="note">{t('rfq.costsOnly')}</p>
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{t('rfq.create')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
