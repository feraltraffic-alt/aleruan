import { useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, type SiteFilter, type SiteStatus } from '../api';
import { SitesMap, type MapPin } from '../components/GoogleMap';
import { LoadError } from '../components/LoadError';
import { Progress, SiteStatusTag } from '../components/siteBits';
import { SITE_STATUSES, siteTone } from '../components/siteData';
import { useWorkTypes } from '../components/dealData';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';

export function SitesPage() {
  const { t, day } = useI18n();
  const navigate = useNavigate();
  const workTypes = useWorkTypes();
  const [filter, setFilter] = useState<SiteFilter>({});
  const [selected, setSelected] = useState<string | null>(null);
  const customers = useLoad(() => api.listCounterparties(undefined, 'CUSTOMER'), []);
  const all = useLoad(() => api.listSites(), []);
  const sites = useLoad(() => api.listSites(filter), [JSON.stringify(filter)]);

  const set = (k: keyof SiteFilter, v: string) => setFilter((f) => ({ ...f, [k]: v || undefined }));
  const countries = useMemo(() => [...new Set((all.data ?? []).map((s) => s.country).filter(Boolean))].sort() as string[], [all.data]);
  const cities = useMemo(() => [...new Set((all.data ?? []).filter((s) => !filter.country || s.country === filter.country)
    .map((s) => s.city).filter(Boolean))].sort() as string[], [all.data, filter.country]);
  const pms = useMemo(() => [...new Set((all.data ?? []).map((s) => s.projectManager).filter(Boolean))].sort() as string[], [all.data]);

  const pins: MapPin[] = useMemo(() => (sites.data ?? [])
    .filter((s) => s.latitude != null && s.longitude != null)
    .map((s) => ({
      id: s.id, lat: s.latitude!, lng: s.longitude!, title: s.name, tone: siteTone(s), href: `/sites/${s.id}`,
      lines: [
        [s.city, s.country].filter(Boolean).join(', '),
        s.customer?.legalName ?? '',
        `${t(`site.status.${s.status}`)}, ${s.progressPct} %`,
        s.lateTasks ? t('site.lateTasks', { n: s.lateTasks }) : '',
      ].filter(Boolean),
    })), [sites.data, t]);
  const select = useCallback((id: string) => setSelected(id), []);
  const filtered = Object.values(filter).some(Boolean);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t('site.title')}</h1>
          <p className="lead">{t('site.lead')}</p>
        </div>
        <div className="moves">
          <Link className="btn" to="/defects">{t('defect.overview')}</Link>
          <Link className="btn" to="/workload">{t('load.title')}</Link>
        </div>
      </div>
      <div className="toolbar filters">
        <select className="select" value={filter.status ?? ''} onChange={(e) => set('status', e.target.value as SiteStatus)}
          aria-label={t('deal.col.status')}>
          <option value="">{t('site.allStatuses')}</option>
          {SITE_STATUSES.map((s) => <option key={s} value={s}>{t(`site.status.${s}`)}</option>)}
        </select>
        <select className="select" value={filter.country ?? ''} aria-label={t('site.country')}
          onChange={(e) => setFilter((f) => ({ ...f, country: e.target.value || undefined, city: undefined }))}>
          <option value="">{t('site.allCountries')}</option>
          {countries.map((c) => <option key={c}>{c}</option>)}
        </select>
        <select className="select" value={filter.city ?? ''} onChange={(e) => set('city', e.target.value)} aria-label={t('site.city')}>
          <option value="">{t('site.allCities')}</option>
          {cities.map((c) => <option key={c}>{c}</option>)}
        </select>
        <select className="select" value={filter.customerId ?? ''} onChange={(e) => set('customerId', e.target.value)}
          aria-label={t('deal.col.customer')}>
          <option value="">{t('site.allCustomers')}</option>
          {customers.data?.map((c) => <option key={c.id} value={c.id}>{c.legalName}</option>)}
        </select>
        <select className="select" value={filter.workType ?? ''} onChange={(e) => set('workType', e.target.value)}
          aria-label={t('line.workType')}>
          <option value="">{t('site.allWorkTypes')}</option>
          {workTypes.list.map((w) => <option key={w.code} value={w.code}>{workTypes.name(w.code)}</option>)}
        </select>
        <select className="select" value={filter.pm ?? ''} onChange={(e) => set('pm', e.target.value)} aria-label={t('site.pm')}>
          <option value="">{t('site.allPms')}</option>
          {pms.map((p) => <option key={p}>{p}</option>)}
        </select>
        {filtered && <button className="linkish" onClick={() => setFilter({})}>{t('site.resetFilters')}</button>}
      </div>
      {sites.error ? <LoadError message={sites.error} onRetry={sites.reload} /> : (
        <div className="sites-layout">
          <SitesMap pins={pins} onSelect={select} selectedId={selected} />
          <div className="panel site-list" aria-busy={sites.loading}>
            {sites.data?.length === 0 ? (
              <p className="empty">{filtered ? t('site.noMatch') : t('site.empty')}</p>
            ) : (
              <ul>
                {sites.data?.map((s) => (
                  <li key={s.id} className={s.id === selected ? 'selected' : ''}>
                    <button className="site-row" onClick={() => navigate(`/sites/${s.id}`)}
                      onMouseEnter={() => setSelected(s.id)}>
                      <span className={`dot t-${siteTone(s)}`} aria-hidden="true" />
                      <span className="site-main">
                        <b>{s.name}</b>
                        <span className="sub">
                          {[s.city, s.country].filter(Boolean).join(', ') || t('site.noAddress')}
                          {s.latitude == null && ` · ${t('site.notOnMap')}`}
                        </span>
                        <span className="sub">{s.customer?.legalName}</span>
                        <span className="site-meta">
                          <SiteStatusTag status={s.status} />
                          <Progress pct={s.progressPct} late={s.lateTasks > 0} />
                          <span className="sub">{s.progressPct} %</span>
                        </span>
                        {(s.plannedStart || s.plannedEnd) && (
                          <span className="sub">{day(s.plannedStart)} – {day(s.plannedEnd)}</span>
                        )}
                        {s.lateTasks > 0 && <span className="sub neg">{t('site.lateTasks', { n: s.lateTasks })}</span>}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </>
  );
}
