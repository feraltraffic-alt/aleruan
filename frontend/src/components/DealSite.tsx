import { Link, useNavigate } from 'react-router-dom';
import { api, type Deal } from '../api';
import { useI18n } from '../i18n';
import { can } from '../permissions';
import { Progress, SiteStatusTag } from './siteBits';
import { useAction } from './useAction';
import { useLoad } from './useLoad';

/** The deal's site, or a button to open one once the customer accepted. */
export function DealSite({ deal }: { deal: Deal }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const action = useAction();
  const sites = useLoad(() => api.listSites({ dealId: deal.id }), [deal.id]);
  const site = sites.data?.[0];
  const canOpen = (deal.status === 'WON' || deal.status === 'IN_PROGRESS') && can('sites.open');
  if (sites.loading || (!site && !canOpen)) return null;

  const open = async () => {
    const s = await action.run(() => api.createSite(deal.id));
    if (s) navigate(`/sites/${s.id}`);
  };

  return (
    <div className="panel deal-site">
      {site ? (
        <>
          <span><span className="sub">{t('site.one')}</span>
            <Link to={`/sites/${site.id}`}><b>{site.name}</b></Link></span>
          <SiteStatusTag status={site.status} />
          <span className="progress-dd"><Progress pct={site.progressPct} late={site.lateTasks > 0} /> {site.progressPct} %</span>
          {site.lateTasks > 0 && <span className="sub neg">{t('site.lateTasks', { n: site.lateTasks })}</span>}
        </>
      ) : (
        <>
          <span>{t('site.openHint')}</span>
          <button className="btn primary" onClick={open} disabled={action.busy}>{t('site.open')}</button>
        </>
      )}
      {action.error && <span className="err">{action.error}</span>}
    </div>
  );
}
