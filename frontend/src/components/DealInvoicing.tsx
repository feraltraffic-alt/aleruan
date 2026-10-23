import { Link, useNavigate } from 'react-router-dom';
import { api, type Deal, type InvoiceSource } from '../api';
import { useI18n } from '../i18n';
import { InvoiceStatusTag } from './invoiceBits';
import { useAction } from './useAction';
import { useLoad } from './useLoad';

/** Invoicing position of a won deal: accepted stages ready to invoice and the invoices so far. */
export function DealInvoicing({ deal }: { deal: Deal }) {
  const { t, money, day } = useI18n();
  const navigate = useNavigate();
  const action = useAction();
  const can = ['WON', 'IN_PROGRESS', 'COMPLETED'].includes(deal.status);
  const pos = useLoad(() => (can ? api.dealInvoicing(deal.id) : Promise.resolve(null)), [deal.id, can]);
  const list = useLoad(() => (can ? api.listInvoices({ dealId: deal.id }) : Promise.resolve([])), [deal.id, can]);
  if (!can) return null;

  const draft = async (source: InvoiceSource, stageId?: string) => {
    const i = await action.run(() => api.draftInvoice(deal.id, source, stageId));
    if (i) navigate(`/invoices/${i.id}`);
  };
  const p = pos.data;
  const open = p?.stages.filter((s) => !s.invoiced) ?? [];

  return (
    <section className="section" aria-label={t('inv.dealTitle')}>
      <div className="section-head">
        <div>
          <h2>{t('inv.dealTitle')}</h2>
          <p className="lead">{t('inv.dealLead')}</p>
        </div>
        <div className="moves">
          <button className="btn" onClick={() => draft('DEAL_LINES')} disabled={action.busy}>{t('inv.fromLines')}</button>
          <button className="btn" onClick={() => draft('EMPTY')} disabled={action.busy}>{t('inv.blank')}</button>
        </div>
      </div>
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      {p && (
        <div className="panel inv-position">
          <div><span>{t('inv.contract')}</span><b>{money(p.contractValue, p.currency)}</b></div>
          <div><span>{t('inv.invoiced')}</span><b>{money(p.invoiced, p.currency)}</b></div>
          <div><span>{t('inv.paid')}</span><b>{money(p.paid, p.currency)}</b></div>
          <div><span>{t('inv.outstanding')}</span><b className={p.outstanding > 0 ? 'neg' : ''}>{money(p.outstanding, p.currency)}</b></div>
          <div><span>{t('inv.remaining')}</span><b>{money(p.remaining, p.currency)}</b></div>
        </div>
      )}
      {open.length > 0 && (
        <div className="panel ready">
          <h3>{t('inv.readyStages')}</h3>
          <ul className="attention">
            {open.map(({ stage }) => (
              <li key={stage.id}>
                <span>{stage.seq}. {stage.title}
                  <span className="sub">{t('inv.acceptedOn', { date: day(stage.acceptedAt), by: stage.acceptedBy ?? '' })}
                    {stage.withRemarks && `, ${t('inv.withRemarks')}`}</span></span>
                <span className="moves"><b>{money(stage.amount, p!.currency)}</b>
                  <button className="btn small primary" onClick={() => draft('STAGE', stage.id)} disabled={action.busy}>
                    {t('inv.invoiceStage')}</button></span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {!!list.data?.length && (
        <div className="panel table-wrap">
          <table>
            <tbody>
              {list.data.map((i) => (
                <tr key={i.id}>
                  <td className="name-cell"><Link to={`/invoices/${i.id}`}>{i.number ?? t('inv.draft')}</Link>
                    {i.kind === 'CREDIT_NOTE' && <span className="sub">{t('inv.creditNote')}</span>}</td>
                  <td>{i.issueDate ? day(i.issueDate) : '—'}</td>
                  <td className="num">{money(i.total, i.currency)}</td>
                  <td><InvoiceStatusTag status={i.status} overdue={i.overdue} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
