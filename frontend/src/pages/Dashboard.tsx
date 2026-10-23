import { Link } from 'react-router-dom';
import { api, type CheckStatus } from '../api';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';

const ORDER: (CheckStatus | 'none')[] = ['RED', 'YELLOW', 'GREEN', 'none'];

export function DashboardPage() {
  const { t } = useI18n();
  const cps = useLoad(() => api.listCounterparties(), []);
  const chain = useLoad(() => api.verifyAudit(), []);

  const counts = { RED: 0, YELLOW: 0, GREEN: 0, none: 0 };
  cps.data?.forEach((c) => counts[c.checkStatus ?? 'none']++);
  const attention = (cps.data ?? [])
    .filter((c) => c.checkStatus === 'RED' || c.checkStatus === 'YELLOW')
    .sort((a, b) => (a.checkStatus === 'RED' ? -1 : 1) - (b.checkStatus === 'RED' ? -1 : 1));

  return (
    <>
      <div className="page-head"><h1>{t('dash.title')}</h1></div>
      <div className="dash">
        <div className="panel box">
          <h2>{t('dash.risk')}</h2>
          <p className="lead" style={{ margin: 0 }}>{t('dash.riskHint')}</p>
          {cps.error ? <LoadError message={cps.error} onRetry={cps.reload} /> : (
            <>
              <div className="riskbar" role="img"
                aria-label={ORDER.map((k) => `${k === 'none' ? t('dash.notChecked') : t(`status.${k}`)}: ${counts[k]}`).join(', ')}>
                {ORDER.map((k) => counts[k] > 0 && <span key={k} className={`s-${k}`} style={{ flexGrow: counts[k] }} />)}
              </div>
              <div className="risklegend">
                {ORDER.map((k) => (
                  <div key={k}>
                    <b>{counts[k]}</b>
                    {k === 'none' ? <span className="status none">{t('dash.notChecked')}</span> : <Status status={k} />}
                  </div>
                ))}
              </div>
              <h2 style={{ marginTop: 26 }}>{t('dash.toCheck')}</h2>
              {attention.length === 0 ? <p className="sub">{t('dash.allGood')}</p> : (
                <ul className="attention">
                  {attention.map((c) => (
                    <li key={c.id}>
                      <Link to={`/counterparties/${c.id}`}>{c.legalName}</Link>
                      <Status status={c.checkStatus} />
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
        <div className="panel box">
          <h2>{t('dash.chain')}</h2>
          {chain.error ? <LoadError message={chain.error} onRetry={chain.reload} /> : chain.data && (
            <div className={`seal${chain.data.valid ? '' : ' broken'}`}>
              <img src="/brand/sphere.png" alt="" />
              <div>
                <strong>{chain.data.valid ? t('dash.chainOk') : t('dash.chainBroken', { seq: chain.data.brokenAtSeq ?? '?' })}</strong>
                <span className="sub">{t('dash.records', { n: chain.data.verifiedRecords })}</span>
              </div>
            </div>
          )}
          <p style={{ marginTop: 16 }}><Link to="/audit">{t('nav.audit')}</Link></p>
        </div>
      </div>
    </>
  );
}
