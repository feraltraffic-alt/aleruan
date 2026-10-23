import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type WorkloadRow } from '../api';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { useLoad } from '../components/useLoad';
import { addDays, localToday, mondayOf } from '../dates';
import { useI18n } from '../i18n';

const LOCALES = { ru: 'ru-RU', cs: 'cs-CZ', en: 'en-GB' } as const;
const SPANS = [4, 8, 12];

export function WorkloadPage() {
  const { t, lang, day } = useI18n();
  const [start, setStart] = useState(() => mondayOf(localToday()));
  const [weeks, setWeeks] = useState(8);
  const [open, setOpen] = useState<{ row: WorkloadRow; monday: string } | null>(null);
  const to = addDays(start, weeks * 7 - 1);
  const load = useLoad(() => api.workload(start, to), [start, to]);
  const fmt = new Intl.DateTimeFormat(LOCALES[lang], { day: 'numeric', month: 'short', timeZone: 'UTC' });
  const label = (d: string) => fmt.format(new Date(`${d}T00:00:00Z`));
  const thisWeek = mondayOf(localToday());

  const inWeek = (row: WorkloadRow, monday: string) =>
    row.tasks.filter((x) => x.start <= addDays(monday, 6) && x.end >= monday);

  return (
    <>
      <Link to="/sites" className="crumb">{t('site.back')}</Link>
      <div className="page-head">
        <div>
          <h1>{t('load.title')}</h1>
          <p className="lead">{t('load.lead')}</p>
        </div>
      </div>
      <div className="toolbar">
        <button className="btn" onClick={() => setStart(addDays(start, -7 * Math.max(1, weeks / 2)))}
          aria-label={t('load.earlier')}>←</button>
        <button className="btn" onClick={() => setStart(thisWeek)} disabled={start === thisWeek}>{t('load.thisWeek')}</button>
        <button className="btn" onClick={() => setStart(addDays(start, 7 * Math.max(1, weeks / 2)))}
          aria-label={t('load.later')}>→</button>
        <select className="select" value={weeks} onChange={(e) => setWeeks(Number(e.target.value))} aria-label={t('load.span')}>
          {SPANS.map((n) => <option key={n} value={n}>{t('load.weeks', { n })}</option>)}
        </select>
        <span className="sub period">{day(start)} – {day(to)}</span>
      </div>
      {load.error ? <LoadError message={load.error} onRetry={load.reload} /> : (
        <div className="panel table-wrap" aria-busy={load.loading}>
          {load.data?.length === 0 ? <p className="empty">{t('load.empty')}</p> : (
            <table className="load">
              <thead>
                <tr>
                  <th>{t('line.subcontractor')}</th>
                  {load.data?.[0]?.weeks.map((w) => (
                    <th key={w.monday} className={`wk${w.monday === thisWeek ? ' now' : ''}`}>{label(w.monday)}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {load.data?.map((row) => (
                  <tr key={row.subcontractor.id}>
                    <td className="name-cell">
                      <Link to={`/counterparties/${row.subcontractor.id}`}>{row.subcontractor.legalName}</Link>
                      <span className="sub">{row.subcontractor.country} <Status status={row.subcontractor.checkStatus} /></span>
                    </td>
                    {row.weeks.map((w) => (
                      <td key={w.monday} className={`wk${w.monday === thisWeek ? ' now' : ''}`}>
                        {w.parallelTasks > 0 ? (
                          <button className={`cell l-${Math.min(w.parallelTasks, 3)}`}
                            onClick={() => setOpen({ row, monday: w.monday })}
                            aria-label={t('load.cell', { n: w.parallelTasks, who: row.subcontractor.legalName, week: label(w.monday) })}>
                            {w.parallelTasks}
                          </button>
                        ) : <span className="cell l-0" aria-hidden="true" />}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      <p className="sub legend">
        <span className="cell l-1" aria-hidden="true">1</span> {t('load.legend1')}{'   '}
        <span className="cell l-2" aria-hidden="true">2</span> {t('load.legend2')}{'   '}
        <span className="cell l-3" aria-hidden="true">3</span> {t('load.legend3')}
      </p>
      {open && (
        <div className="panel box week-detail" role="region" aria-label={t('load.weekOf', { week: label(open.monday) })}>
          <div className="section-head">
            <h2>{open.row.subcontractor.legalName}, {t('load.weekOf', { week: label(open.monday) })}</h2>
            <button className="linkish" onClick={() => setOpen(null)}>{t('load.close')}</button>
          </div>
          <ul className="attention">
            {inWeek(open.row, open.monday).map((x) => (
              <li key={x.taskId}>
                <span><Link to={`/sites/${x.siteId}`}>{x.siteName}</Link><span className="sub">{x.title}</span></span>
                <span className="sub">{day(x.start)} – {day(x.end)}, {x.progressPct} %</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}
