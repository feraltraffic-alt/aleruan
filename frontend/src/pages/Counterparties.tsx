import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, type Counterparty, type CounterpartyRole } from '../api';
import { LoadError } from '../components/LoadError';
import { Status, Stripe } from '../components/Status';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { CounterpartyForm } from './CounterpartyForm';
import { ROLES } from '../constants';



export function CounterpartiesPage() {
  const { t, date } = useI18n();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [role, setRole] = useState<CounterpartyRole | ''>('');
  const [adding, setAdding] = useState(false);
  const { data, error, loading, reload } = useLoad(
    () => api.listCounterparties(q || undefined, role || undefined), [q, role]);

  const created = (c: Counterparty) => {
    setAdding(false);
    navigate(`/counterparties/${c.id}`, { state: { justCreated: true } });
  };

  return (
    <>
      <div className="page-head">
        <h1>{t('cp.title')}</h1>
        <button className="btn primary" onClick={() => setAdding(true)}>{t('cp.add')}</button>
      </div>
      <div className="toolbar">
        <input className="input search" type="search" value={q} onChange={(e) => setQ(e.target.value)}
          placeholder={t('cp.search')} aria-label={t('cp.search')} />
        <select className="select" value={role} onChange={(e) => setRole(e.target.value as CounterpartyRole | '')}
          aria-label={t('cp.col.roles')}>
          <option value="">{t('cp.allRoles')}</option>
          {ROLES.map((r) => <option key={r} value={r}>{t(`role.${r}`)}</option>)}
        </select>
      </div>
      {error ? <LoadError message={error} onRetry={reload} /> : (
        <div className="panel table-wrap" aria-busy={loading}>
          {data && data.length === 0 ? (
            <p className="empty">{q || role ? t('cp.noMatch') : t('cp.empty')}</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th className="stripe" aria-hidden="true" />
                  <th>{t('cp.col.name')}</th>
                  <th>{t('cp.col.country')}</th>
                  <th>{t('cp.col.ids')}</th>
                  <th>{t('cp.col.roles')}</th>
                  <th>{t('cp.col.checked')}</th>
                </tr>
              </thead>
              <tbody>
                {data?.map((c) => (
                  <tr key={c.id} className="row" onClick={() => navigate(`/counterparties/${c.id}`)}>
                    <Stripe status={c.checkStatus} />
                    <td className="name-cell">
                      <Link to={`/counterparties/${c.id}`} onClick={(e) => e.stopPropagation()}>{c.legalName}</Link>
                      {c.address && <span className="sub">{c.address}</span>}
                    </td>
                    <td>{c.country}</td>
                    <td className="mono-ish">
                      {c.companyId ?? '—'}
                      {c.vatNumber && <span className="sub">{c.vatNumber}</span>}
                    </td>
                    <td><span className="roles">{c.roles.map((r) => <span key={r} className="tag">{t(`role.${r}`)}</span>)}</span></td>
                    <td>
                      <Status status={c.checkStatus} />
                      {c.lastCheckedAt && <span className="sub">{date(c.lastCheckedAt)}</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      {adding && <CounterpartyForm onClose={() => setAdding(false)} onCreated={created} />}
    </>
  );
}
