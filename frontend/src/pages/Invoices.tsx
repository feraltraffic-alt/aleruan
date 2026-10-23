import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, type InvoiceStatus } from '../api';
import { LoadError } from '../components/LoadError';
import { InvoiceStatusTag } from '../components/invoiceBits';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';

const STATUSES: InvoiceStatus[] = ['DRAFT', 'ISSUED', 'PAID', 'CREDITED'];

export function InvoicesPage() {
  const { t, day, money } = useI18n();
  const navigate = useNavigate();
  const [status, setStatus] = useState<InvoiceStatus | ''>('');
  const [overdue, setOverdue] = useState(false);
  const list = useLoad(() => api.listInvoices({ status: status || undefined, overdue }), [status, overdue]);

  // What customers owe, per currency: issued and not fully paid
  const owed = new Map<string, { open: number; late: number }>();
  list.data?.filter((i) => i.status === 'ISSUED' && i.kind === 'INVOICE').forEach((i) => {
    const o = owed.get(i.currency) ?? { open: 0, late: 0 };
    const rest = i.total - i.paidAmount;
    o.open += rest;
    if (i.overdue) o.late += rest;
    owed.set(i.currency, o);
  });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t('inv.title')}</h1>
          <p className="lead">{t('inv.lead')}</p>
        </div>
        <Link className="btn" to="/invoices/money-s4">{t('money.title')}</Link>
      </div>
      {owed.size > 0 && !status && !overdue && (
        <div className="panel owed">
          {[...owed.entries()].map(([cur, o]) => (
            <div key={cur}>
              <span>{t('inv.owed', { cur })}</span>
              <b>{money(o.open, cur)}</b>
              {o.late > 0 && <span className="sub neg">{t('inv.owedLate', { sum: money(o.late, cur) })}</span>}
            </div>
          ))}
        </div>
      )}
      <div className="toolbar">
        <select className="select" value={status} onChange={(e) => setStatus(e.target.value as InvoiceStatus | '')}
          aria-label={t('deal.col.status')}>
          <option value="">{t('inv.allStatuses')}</option>
          {STATUSES.map((s) => <option key={s} value={s}>{t(`inv.status.${s}`)}</option>)}
        </select>
        <label className="toggle"><input type="checkbox" checked={overdue} onChange={(e) => setOverdue(e.target.checked)} /> {t('inv.onlyOverdue')}</label>
      </div>
      {list.error ? <LoadError message={list.error} onRetry={list.reload} /> : (
        <div className="panel table-wrap" aria-busy={list.loading}>
          {list.data?.length === 0 ? <p className="empty">{status || overdue ? t('inv.noMatch') : t('inv.empty')}</p> : (
            <table>
              <thead>
                <tr>
                  <th>{t('inv.col.number')}</th><th>{t('deal.col.customer')}</th><th>{t('inv.col.issued')}</th>
                  <th>{t('inv.col.due')}</th><th className="num">{t('inv.col.total')}</th><th>{t('deal.col.status')}</th>
                </tr>
              </thead>
              <tbody>
                {list.data?.map((i) => (
                  <tr key={i.id} className="row" onClick={() => navigate(`/invoices/${i.id}`)}>
                    <td className="name-cell">
                      <Link to={`/invoices/${i.id}`} onClick={(e) => e.stopPropagation()}>
                        {i.number ?? t('inv.draft')}
                      </Link>
                      <span className="sub">{i.kind === 'CREDIT_NOTE' ? t('inv.creditNote') : ''}{i.dealNumber} {i.dealTitle}</span>
                    </td>
                    <td>{i.customer?.legalName ?? '—'}</td>
                    <td>{i.issueDate ? day(i.issueDate) : '—'}</td>
                    <td className={i.overdue ? 'neg' : ''}>{i.status === 'DRAFT' ? '—' : day(i.dueDate)}</td>
                    <td className="num">{money(i.total, i.currency)}
                      {i.paidAmount > 0 && i.status !== 'PAID' && <span className="sub">{t('inv.paidPart', { sum: money(i.paidAmount, i.currency) })}</span>}
                    </td>
                    <td><InvoiceStatusTag status={i.status} overdue={i.overdue} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </>
  );
}

/** Placeholder until the accountant's Money S4 import sample arrives. */
export function MoneyExportPage() {
  const { t } = useI18n();
  return (
    <>
      <Link to="/invoices" className="crumb">{t('inv.back')}</Link>
      <div className="page-head"><h1>{t('money.title')}</h1></div>
      <div className="panel box wip">
        <span className="pill d-QUOTED">{t('money.wip')}</span>
        <p>{t('money.what')}</p>
        <p className="sub">{t('money.waiting')}</p>
      </div>
    </>
  );
}
