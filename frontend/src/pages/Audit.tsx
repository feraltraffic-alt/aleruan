import { api } from '../api';
import { LoadError } from '../components/LoadError';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';

export function AuditPage() {
  const { t } = useI18n();
  const chain = useLoad(() => api.verifyAudit(), []);
  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t('audit.title')}</h1>
          <p className="lead">{t('audit.lead')}</p>
        </div>
        <button className="btn primary" onClick={chain.reload} disabled={chain.loading}>{t('audit.verify')}</button>
      </div>
      {chain.error ? <LoadError message={chain.error} onRetry={chain.reload} /> : chain.data && (
        <div className="panel box" role="status">
          <div className={`seal${chain.data.valid ? '' : ' broken'}`}>
            <img src="/brand/sphere.png" alt="" />
            <div>
              <strong>{chain.data.valid ? t('dash.chainOk') : t('dash.chainBroken', { seq: chain.data.brokenAtSeq ?? '?' })}</strong>
              <span className="sub">{t('dash.records', { n: chain.data.verifiedRecords })}</span>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
