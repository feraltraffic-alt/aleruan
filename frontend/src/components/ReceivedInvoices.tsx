import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type SupplierInvoice } from '../api';
import { localToday } from '../dates';
import { useI18n } from '../i18n';
import { can } from '../permissions';
import { LoadError } from './LoadError';
import { Status } from './Status';
import { useAction } from './useAction';
import { useLoad } from './useLoad';

/** Invoices received from subcontractors and suppliers: what Aleruan owes, with the bank account check. */
export function ReceivedInvoices({ dealId }: { dealId?: string }) {
  const { t, day, money } = useI18n();
  const list = useLoad(() => api.listSupplierInvoices({ dealId }), [dealId]);
  const action = useAction();
  const [paying, setPaying] = useState<string | null>(null);
  const [paidOn, setPaidOn] = useState(localToday());

  const owed = new Map<string, number>();
  list.data?.filter((i) => !i.paidOn).forEach((i) => owed.set(i.currency, (owed.get(i.currency) ?? 0) + i.total));

  const pay = async (i: SupplierInvoice) => {
    const next = await action.run(() => api.paySupplierInvoice(i.id, paidOn));
    if (next) { setPaying(null); list.reload(); }
  };
  const open = async (i: SupplierInvoice) => {
    const b = await action.run(() => api.supplierInvoiceFile(i.id));
    if (b) window.open(URL.createObjectURL(b), '_blank', 'noopener');
  };

  return (
    <>
      {!dealId && owed.size > 0 && (
        <div className="panel owed">
          {[...owed.entries()].map(([cur, sum]) => (
            <div key={cur}><span>{t('rcv.owe', { cur })}</span><b>{money(sum, cur)}</b></div>
          ))}
        </div>
      )}
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      {list.error ? <LoadError message={list.error} onRetry={list.reload} /> : (
        <div className="panel table-wrap" aria-busy={list.loading}>
          {list.data?.length === 0 ? <p className="empty">{t('rcv.empty')}</p> : (
            <table>
              <thead>
                <tr>
                  <th>{t('inv.col.number')}</th><th>{t('rcv.col.supplier')}</th><th>{t('inv.col.issued')}</th>
                  <th>{t('inv.col.due')}</th><th className="num">{t('inv.col.total')}</th><th>{t('rcv.col.account')}</th>
                  <th>{t('rcv.col.paid')}</th>
                </tr>
              </thead>
              <tbody>
                {list.data?.map((i) => (
                  <tr key={i.id}>
                    <td className="name-cell">
                      {i.hasFile ? <button className="linkish" onClick={() => open(i)}>{i.number}</button> : i.number}
                      <span className="sub">{i.dealId ? <Link to={`/deals/${i.dealId}`}>{i.dealNumber}</Link> : t('rcv.noDeal')}
                        {i.importId && <> · <Link to={`/import/${i.importId}`}>{t('rcv.fromImport')}</Link></>}</span>
                    </td>
                    <td><Link to={`/counterparties/${i.supplier.id}`}>{i.supplier.legalName}</Link>
                      <span className="sub"><Status status={i.supplier.checkStatus} /></span></td>
                    <td>{i.issueDate ? day(i.issueDate) : '—'}</td>
                    <td className={i.overdue ? 'neg' : ''}>{i.dueDate ? day(i.dueDate) : '—'}</td>
                    <td className="num">{money(i.total, i.currency)}</td>
                    <td>{i.bankAccount ? <><Status status={i.accountCheck} /><span className="sub">{i.bankAccount}</span></>
                      : <span className="sub">{t('rcv.noAccount')}</span>}</td>
                    <td>
                      {i.paidOn ? day(i.paidOn) : paying === i.id ? (
                        <span className="pay-inline">
                          <input className="input" type="date" value={paidOn} max={localToday()}
                            onChange={(e) => setPaidOn(e.target.value)} aria-label={t('rcv.paidOn')} />
                          <button className="btn small primary" onClick={() => pay(i)} disabled={action.busy}>{t('rcv.save')}</button>
                        </span>
                      ) : can('invoices.write') ? (
                        <button className="btn small" onClick={() => { setPaying(i.id); setPaidOn(localToday()); }}>{t('rcv.markPaid')}</button>
                      ) : '—'}
                    </td>
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
