import type { Site, SiteTask } from '../api';
import { useI18n } from '../i18n';
import { addDays, mondayOf } from '../dates';

const LOCALES = { ru: 'ru-RU', cs: 'cs-CZ', en: 'en-GB' } as const;
const DAY = 86_400_000;
const toTime = (s: string) => new Date(`${s}T00:00:00Z`).getTime();

/** Calendar-style schedule: one bar per task across the site's weeks, with today marked. */
export function Schedule({ site, today, onOpen }: { site: Site; today: string; onOpen?(task: SiteTask): void }) {
  const { t, lang } = useI18n();
  const dates = [site.plannedStart, site.plannedEnd, ...site.tasks.flatMap((x) => [x.start, x.end])]
    .filter((d): d is string => !!d).sort();
  if (!site.tasks.length || !dates.length) return null;

  const first = mondayOf(dates[0]);
  const last = addDays(mondayOf(dates[dates.length - 1]), 6);
  const span = (toTime(last) - toTime(first)) / DAY + 1;
  const pos = (d: string) => ((toTime(d) - toTime(first)) / DAY / span) * 100;
  const weeks: string[] = [];
  for (let m = first; m <= last; m = addDays(m, 7)) weeks.push(m);
  const fmt = new Intl.DateTimeFormat(LOCALES[lang], { day: 'numeric', month: 'short', timeZone: 'UTC' });
  const label = (d: string) => fmt.format(new Date(`${d}T00:00:00Z`));
  const showToday = today >= first && today <= last;

  return (
    <div className="gantt" role="table" aria-label={t('sched.title')}>
      <div className="g-row g-head" role="row">
        <div className="g-label" role="columnheader">{t('sched.task')}</div>
        <div className="g-track" role="columnheader">
          {weeks.map((w) => (
            <span key={w} className="g-week" style={{ left: `${pos(w)}%`, width: `${(7 / span) * 100}%` }}>{label(w)}</span>
          ))}
        </div>
      </div>
      {site.plannedStart && site.plannedEnd && (
        <div className="g-row g-plan" role="row">
          <div className="g-label" role="cell"><span className="sub">{t('sched.planned')}</span></div>
          <div className="g-track" role="cell">
            <span className="g-planbar" style={{ left: `${pos(site.plannedStart)}%`,
              width: `${((toTime(site.plannedEnd) - toTime(site.plannedStart)) / DAY + 1) / span * 100}%` }} />
          </div>
        </div>
      )}
      {site.tasks.map((task) => {
        const width = ((toTime(task.end) - toTime(task.start)) / DAY + 1) / span * 100;
        return (
          <div key={task.id} className="g-row" role="row">
            <div className="g-label" role="cell">
              <span className="g-title">{task.title}</span>
              <span className="sub">{task.subcontractor?.legalName ?? t('line.noSubcontractor')}</span>
            </div>
            <div className="g-track" role="cell">
              {weeks.map((w) => <span key={w} className="g-grid" style={{ left: `${pos(w)}%` }} />)}
              {showToday && <span className="g-today" style={{ left: `${pos(today)}%` }} />}
              <button className={`g-bar${task.late ? ' late' : ''}${task.progressPct === 100 ? ' done' : ''}`}
                style={{ left: `${pos(task.start)}%`, width: `${width}%` }} onClick={() => onOpen?.(task)}
                title={`${task.title}: ${label(task.start)} – ${label(task.end)}, ${task.progressPct} %`}
                aria-label={t('sched.bar', { what: task.title, from: label(task.start), to: label(task.end), pct: task.progressPct })}>
                <span className="g-fill" style={{ width: `${task.progressPct}%` }} />
                <span className="g-pct">{task.progressPct} %</span>
              </button>
            </div>
          </div>
        );
      })}
      {showToday && (
        <div className="g-row g-foot" role="row">
          <div className="g-label" role="cell" />
          <div className="g-track" role="cell">
            <span className="g-todaylabel" style={{ left: `${pos(today)}%` }}>{t('sched.today')}</span>
          </div>
        </div>
      )}
    </div>
  );
}
