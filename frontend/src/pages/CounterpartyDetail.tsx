import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, type CheckFinding, type CheckResult } from '../api';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';

export function CounterpartyDetailPage() {
  const { id = '' } = useParams();
  const { t, date } = useI18n();
  const [checking, setChecking] = useState(false);
  const [checkError, setCheckError] = useState<string | null>(null);
  const [lastFindings, setLastFindings] = useState<CheckFinding[] | null>(null);

  const [account, setAccount] = useState('');
  const [accountResult, setAccountResult] = useState<CheckResult | null>(null);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [accountBusy, setAccountBusy] = useState(false);

  const cp = useLoad(() => api.getCounterparty(id), [id]);
  const history = useLoad(() => api.checkHistory(id), [id]);
  const audit = useLoad(() => api.auditHistory('Counterparty', id), [id]);

  const runCheck = async () => {
    setChecking(true);
    setCheckError(null);
    try {
      const run = await api.runChecks(id);
      setLastFindings(run.findings);
      await Promise.all([cp.reload(), history.reload(), audit.reload()]);
    } catch (e) {
      setCheckError(e instanceof Error ? e.message : String(e));
    } finally {
      setChecking(false);
    }
  };

  const checkAccount = async (e: FormEvent) => {
    e.preventDefault();
    setAccountResult(null);
    if (!account.trim()) { setAccountError(t('acct.required')); return; }
    setAccountBusy(true);
    setAccountError(null);
    try {
      setAccountResult(await api.checkAccount(id, account));
      await Promise.all([history.reload(), audit.reload()]);
    } catch (err) {
      setAccountError(err instanceof Error ? err.message : String(err));
    } finally {
      setAccountBusy(false);
    }
  };

  const accountMessage = (r: CheckResult) => {
    if (r.findings.includes('MANUAL_REVIEW_REQUIRED')) return t('acct.manual');
    if (r.findings.includes('SOURCE_UNAVAILABLE')) return t('acct.unavailable');
    return r.findings.includes('ACCOUNT_NOT_PUBLISHED') ? t('acct.bad') : t('acct.ok');
  };

  if (cp.error) return <LoadError message={cp.error} onRetry={cp.reload} />;
  const c = cp.data;
  if (!c) return <p aria-busy="true" />;

  const manual = lastFindings?.length === 1 && lastFindings[0] === 'MANUAL_REVIEW_REQUIRED';

  return (
    <>
      <Link to="/counterparties" className="crumb">{t('detail.back')}</Link>
      <div className="page-head">
        <div>
          <h1>{c.legalName}</h1>
          <span className="roles" style={{ marginTop: 8 }}>
            {c.roles.map((r) => <span key={r} className="tag">{t(`role.${r}`)}</span>)}
          </span>
        </div>
      </div>

      <div className="panel">
        <div className={`verdict ${c.checkStatus ?? ''}`}>
          <Status status={c.checkStatus} />
          <span className="when">{c.lastCheckedAt ? t('detail.lastCheck', { date: date(c.lastCheckedAt) }) : t('cp.never')}</span>
          <button className="btn primary" onClick={runCheck} disabled={checking}>
            {checking ? t('detail.checking') : t('detail.check')}
          </button>
        </div>
        {checkError && <p className="note bad" role="alert" style={{ margin: '12px 22px 0' }}>{checkError}</p>}
        {manual && <p className="note" role="status" style={{ margin: '12px 22px 0' }}>{t('detail.manual')}</p>}
        <dl className="facts">
          <div><dt>{t('form.country')}</dt><dd>{c.country}</dd></div>
          <div><dt>{t('form.companyId')}</dt><dd>{c.companyId ?? '—'}</dd></div>
          <div><dt>{t('form.vat')}</dt><dd>{c.vatNumber ?? '—'}</dd></div>
          <div><dt>{t('form.address')}</dt><dd>{c.address ?? '—'}</dd></div>
          <div><dt>{t('form.email')}</dt><dd>{c.email ?? '—'}</dd></div>
          <div><dt>{t('form.phone')}</dt><dd>{c.phone ?? '—'}</dd></div>
        </dl>
      </div>

      <section className="section">
        <h2>{t('acct.title')}</h2>
        <form className="panel box account-form" onSubmit={checkAccount} noValidate>
          <p className="lead" style={{ marginTop: 0 }}>{t('acct.lead')}</p>
          <div className="fill-row">
            <input className="input" value={account} onChange={(e) => { setAccount(e.target.value); setAccountError(null); }}
              aria-label={t('acct.input')} placeholder={t('acct.input')} aria-invalid={!!accountError} />
            <button className="btn primary" disabled={accountBusy}>{t('acct.check')}</button>
          </div>
          {accountError && <p className="note bad" role="alert" style={{ marginTop: 12 }}>{accountError}</p>}
          {accountResult && (
            <p className={`verdict-line ${accountResult.status}`} role="status">
              <Status status={accountResult.status} /> {accountMessage(accountResult)}
              {accountResult.requestId && <span className="sub">{t('detail.col.evidence')}: {accountResult.requestId}</span>}
            </p>
          )}
        </form>
      </section>

      <section className="section">
        <h2>{t('detail.history')}</h2>
        {history.error ? <LoadError message={history.error} onRetry={history.reload} /> : (
          <div className="panel table-wrap">
            {history.data?.length === 0 ? <p className="empty">{t('detail.noChecks')}</p> : (
              <table>
                <thead>
                  <tr>
                    <th>{t('detail.col.date')}</th>
                    <th>{t('detail.col.source')}</th>
                    <th>{t('detail.col.result')}</th>
                    <th>{t('detail.col.findings')}</th>
                    <th>{t('detail.col.evidence')}</th>
                  </tr>
                </thead>
                <tbody>
                  {history.data?.map((r) => (
                    <tr key={r.id}>
                      <td>{date(r.checkedAt)}</td>
                      <td>{t(`src.${r.source}`)}</td>
                      <td><Status status={r.status} /></td>
                      <td>
                        {r.findings.length === 0 ? <span className="sub">{t('detail.clean')}</span> : (
                          <ul className="findings">{r.findings.map((f) => <li key={f}>{t(`find.${f}`)}</li>)}</ul>
                        )}
                        {r.checkedAccount && <span className="sub">{t('detail.account', { acc: r.checkedAccount })}</span>}
                        {r.unavailableReason && <span className="sub">{r.unavailableReason}</span>}
                      </td>
                      <td className="mono-ish">{r.requestId ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>

      <section className="section">
        <h2>{t('detail.audit')}</h2>
        <div className="panel table-wrap">
          {audit.data?.length === 0 ? <p className="empty">{t('detail.noAudit')}</p> : (
            <table>
              <tbody>
                {audit.data?.map((a) => (
                  <tr key={a.seq}>
                    <td style={{ width: 70 }}>№{a.seq}</td>
                    <td>{date(a.occurredAt)}</td>
                    <td>{['CREATED', 'CHECKED', 'ACCOUNT_CHECKED'].includes(a.action) ? t(`action.${a.action as 'CREATED' | 'CHECKED' | 'ACCOUNT_CHECKED'}`) : a.action}</td>
                    <td>{a.actor}</td>
                    <td className="hash">{a.hash.slice(0, 16)}…</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </>
  );
}
