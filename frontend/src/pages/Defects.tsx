import { Link } from 'react-router-dom';
import { api } from '../api';
import { LoadError } from '../components/LoadError';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { DefectCard } from './SiteDefects';

/** Unresolved defects on all sites, overdue and critical first. */
export function DefectsPage() {
  const { t } = useI18n();
  const defects = useLoad(() => api.unresolvedDefects(), []);
  const rank = (d: { overdue: boolean; severity: string }) => (d.overdue ? 0 : 2) + (d.severity === 'CRITICAL' ? 0 : 1);
  const list = [...(defects.data ?? [])].sort((a, b) => rank(a) - rank(b));
  const overdue = list.filter((d) => d.overdue).length;
  return (
    <>
      <Link to="/sites" className="crumb">{t('site.back')}</Link>
      <div className="page-head">
        <div>
          <h1>{t('defect.overview')}</h1>
          <p className="lead">{defects.data ? t('defect.overviewLead', { n: list.length, overdue }) : ''}</p>
        </div>
      </div>
      {defects.error ? <LoadError message={defects.error} onRetry={defects.reload} /> : (
        list.length === 0 && defects.data ? <div className="panel"><p className="empty">{t('defect.noneOpen')}</p></div> : (
          <div className="defects">
            {list.map((d) => <DefectCard key={d.id} defect={d} showSite
              onChange={(n) => defects.setData((l) => (l ?? []).map((x) => (x.id === n.id ? n : x)))} />)}
          </div>
        )
      )}
    </>
  );
}
