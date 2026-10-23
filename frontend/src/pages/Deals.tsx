import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, type Deal, type DealStatus } from '../api';
import { LoadError } from '../components/LoadError';
import { Stripe } from '../components/Status';
import { DealStatusTag } from '../components/dealBits';
import { DEAL_STATUSES } from '../components/dealData';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { DealForm } from './DealForm';

export function DealsPage() {
  const { t, date, money, num } = useI18n();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<DealStatus | ''>('');
  const [adding, setAdding] = useState(false);
  const { data, error, loading, reload } = useLoad(() => api.listDeals(q || undefined, status || undefined), [q, status]);

  const created = (d: Deal) => {
    setAdding(false);
    navigate(`/deals/${d.id}`);
  };

  return (
    <>
      <div className="page-head">
        <h1>{t('deal.title')}</h1>
        <button className="btn primary" onClick={() => setAdding(true)}>{t('deal.add')}</button>
      </div>
      <div className="toolbar">
        <input className="input search" type="search" value={q} onChange={(e) => setQ(e.target.value)}
          placeholder={t('deal.search')} aria-label={t('deal.search')} />
        <select className="select" value={status} onChange={(e) => setStatus(e.target.value as DealStatus | '')}
          aria-label={t('deal.col.status')}>
          <option value="">{t('deal.allStatuses')}</option>
          {DEAL_STATUSES.map((s) => <option key={s} value={s}>{t(`deal.status.${s}`)}</option>)}
        </select>
      </div>
      {error ? <LoadError message={error} onRetry={reload} /> : (
        <div className="panel table-wrap" aria-busy={loading}>
          {data && data.length === 0 ? (
            <p className="empty">{q || status ? t('deal.noMatch') : t('deal.empty')}</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th className="stripe" aria-hidden="true" />
                  <th>{t('deal.col.deal')}</th>
                  <th>{t('deal.col.customer')}</th>
                  <th>{t('deal.col.status')}</th>
                  <th className="num">{t('deal.col.price')}</th>
                  <th className="num">{t('deal.col.margin')}</th>
                  <th>{t('deal.col.created')}</th>
                </tr>
              </thead>
              <tbody>
                {data?.map((d) => (
                  <tr key={d.id} className="row" onClick={() => navigate(`/deals/${d.id}`)}>
                    <Stripe status={d.customer?.checkStatus ?? null} />
                    <td className="name-cell">
                      <Link to={`/deals/${d.id}`} onClick={(e) => e.stopPropagation()}>{d.title}</Link>
                      <span className="sub">{d.number} · {t(`dir.${d.direction}`)}</span>
                    </td>
                    <td>{d.customer?.legalName ?? '—'}</td>
                    <td><DealStatusTag status={d.status} /></td>
                    <td className="num">{money(d.totals.price, d.currency)}</td>
                    <td className="num">
                      {money(d.totals.margin, d.currency)}
                      {d.totals.marginPct != null && <span className="sub">{num(d.totals.marginPct, 1)} %</span>}
                    </td>
                    <td>{date(d.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      {adding && <DealForm onClose={() => setAdding(false)} onCreated={created} />}
    </>
  );
}
