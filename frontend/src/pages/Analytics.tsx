import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type MarginCut, type MarginFilter, type MarginRow, type MarginScope } from '../api';
import { DIRECTIONS, useWorkTypes } from '../components/dealData';
import { useCheckText } from '../components/evidenceText';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { useLoad } from '../components/useLoad';
import { localToday } from '../dates';
import { useI18n, type MessageKey } from '../i18n';

const CUTS: MarginCut[] = ['DEAL', 'CUSTOMER', 'SUBCONTRACTOR', 'WORK_TYPE', 'DIRECTION', 'COUNTRY', 'MONTH'];
const CURRENCIES = ['CZK', 'EUR', 'PLN'];
const NONE = 'NONE';

type Period = 'all' | 'year' | 'quarter' | 'custom';

function periodRange(p: Period): { from?: string; to?: string } {
  const today = localToday();
  if (p === 'year') return { from: `${today.slice(0, 4)}-01-01`, to: today };
  if (p === 'quarter') {
    const d = new Date(`${today}T00:00:00Z`);
    d.setUTCDate(d.getUTCDate() - 90);
    return { from: d.toISOString().slice(0, 10), to: today };
  }
  return {};
}

/** Margin by deal and other cuts in one currency, how sites are doing, and what is risky. */
export function AnalyticsPage() {
  const { t } = useI18n();
  const [scope, setScope] = useState<MarginScope>('CONTRACTED');
  const [period, setPeriod] = useState<Period>('all');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [direction, setDirection] = useState('');
  const [country, setCountry] = useState('');
  const [currency, setCurrency] = useState('CZK');
  const [cut, setCut] = useState<MarginCut>('DEAL');

  const range = period === 'custom' ? { from: from || undefined, to: to || undefined } : periodRange(period);
  const filter: MarginFilter = { scope, ...range, direction: direction || undefined, country: country || undefined, currency };
  const margin = useLoad(() => api.analyticsMargin(filter), [JSON.stringify(filter)]);
  const health = useLoad(() => api.analyticsHealth(), []);
  const countries = useLoad(() => api.analyticsMargin({ scope, currency: 'CZK' }), [scope]);
  const countryOptions = (countries.data?.cuts.COUNTRY ?? []).map((r) => r.key).filter((k) => k !== NONE).sort();

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t('an.title')}</h1>
          <p className="lead">{t('an.lead')}</p>
        </div>
      </div>

      <div className="toolbar filters an-filters" role="group" aria-label={t('an.filters')}>
        <div className="segmented" role="radiogroup" aria-label={t('an.scope')}>
          {(['CONTRACTED', 'PIPELINE'] as MarginScope[]).map((s) => (
            <button key={s} role="radio" aria-checked={scope === s} className={scope === s ? 'on' : ''}
              onClick={() => setScope(s)}>{t(`an.scope.${s}`)}</button>
          ))}
        </div>
        <select className="select" value={period} onChange={(e) => setPeriod(e.target.value as Period)}
          aria-label={t('an.period')}>
          {(['all', 'year', 'quarter', 'custom'] as Period[]).map((p) => <option key={p} value={p}>{t(`an.period.${p}`)}</option>)}
        </select>
        {period === 'custom' && (
          <>
            <input className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label={t('an.from')} />
            <input className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label={t('an.to')} />
          </>
        )}
        <select className="select" value={direction} onChange={(e) => setDirection(e.target.value)} aria-label={t('an.direction')}>
          <option value="">{t('an.allDirections')}</option>
          {DIRECTIONS.map((d) => <option key={d} value={d}>{t(`dir.${d}`)}</option>)}
        </select>
        <select className="select" value={country} onChange={(e) => setCountry(e.target.value)} aria-label={t('an.country')}>
          <option value="">{t('an.allCountries')}</option>
          {[...new Set([...countryOptions, ...(country ? [country] : [])])].map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <select className="select narrow" value={currency} onChange={(e) => setCurrency(e.target.value)} aria-label={t('an.currency')}>
          {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>

      {margin.error ? <LoadError message={margin.error} onRetry={margin.reload} /> : !margin.data ? (
        <p className="empty" aria-busy="true">{t('loading')}</p>
      ) : (
        <MarginSection report={margin.data} cut={cut} setCut={setCut} busy={margin.loading} />
      )}

      {health.error ? <LoadError message={health.error} onRetry={health.reload} /> : !health.data ? (
        <p className="empty" aria-busy="true">{t('loading')}</p>
      ) : <HealthSections health={health.data} />}
    </>
  );
}

function MarginSection({ report, cut, setCut, busy }: {
  report: NonNullable<Awaited<ReturnType<typeof api.analyticsMargin>>>; cut: MarginCut; setCut(c: MarginCut): void;
  busy: boolean;
}) {
  const { t, money, num, day, lang } = useI18n();
  const wt = useWorkTypes();
  const tot = report.totals;
  const rows = report.cuts[cut];
  const peak = Math.max(1, ...rows.map((r) => Math.abs(r.margin)));
  const label = (r: MarginRow) => {
    if (r.key === NONE) return t(`an.none.${cut}` as MessageKey);
    if (cut === 'WORK_TYPE') return wt.name(r.label);
    if (cut === 'DIRECTION') return t(`dir.${r.label}` as MessageKey);
    if (cut === 'MONTH') {
      const [y, m] = r.key.split('-');
      return new Date(Date.UTC(+y, +m - 1, 1)).toLocaleDateString({ ru: 'ru-RU', cs: 'cs-CZ', en: 'en-GB' }[lang], { month: 'long', year: 'numeric', timeZone: 'UTC' });
    }
    return r.label;
  };
  const rateText = (v: number) => new Intl.NumberFormat({ ru: 'ru-RU', cs: 'cs-CZ', en: 'en-GB' }[lang],
    { maximumSignificantDigits: 5 }).format(v);
  const pctText = (v: number | null) => (v == null ? '—' : `${num(v, 1)} %`);

  return (
    <section className="section" aria-label={t('an.margin')} aria-busy={busy}>
      <div className="panel an-kpis">
        <div><span>{t('an.kpi.price')}</span><b>{money(tot.price, report.currency)}</b></div>
        <div><span>{t('an.kpi.cost')}</span><b>{money(tot.cost, report.currency)}</b></div>
        <div className="lead-figure"><span>{t('an.kpi.margin')}</span>
          <b className={tot.margin < 0 ? 'neg' : ''}>{money(tot.margin, report.currency)}</b>
          <em>{pctText(tot.marginPct)}</em></div>
        <div><span>{t('an.kpi.deals')}</span><b>{tot.deals}</b></div>
      </div>
      <p className="note an-rates">
        {t('an.rates', { date: day(report.ratesOn) })}{' '}
        {report.rates.filter((r) => r.currency !== 'CZK').map((r) => `${r.currency} ${rateText(r.czkPerUnit)}`).join(', ') || '—'}
        {report.missingRates.length > 0 && <> · <b>{t('an.missingRates', { list: report.missingRates.join(', ') })}</b></>}
      </p>

      <div className="tabs an-tabs" role="tablist" aria-label={t('an.cut')}>
        {CUTS.map((c) => (
          <button key={c} role="tab" aria-selected={cut === c} className={cut === c ? 'on' : ''} onClick={() => setCut(c)}>
            {t(`an.cut.${c}`)}
          </button>
        ))}
      </div>
      <div className="panel table-wrap an-table">
        {rows.length === 0 ? <p className="empty">{t('an.empty')}</p> : (
          <table>
            <thead>
              <tr>
                <th>{t(`an.cut.${cut}`)}</th>
                <th className="num">{t('an.col.deals')}</th>
                <th className="num">{t('an.col.price')}</th>
                <th className="num">{t('an.col.cost')}</th>
                <th className="num">{t('an.col.margin')}</th>
                <th className="num">{t('an.col.pct')}</th>
                <th className="an-bar-col" aria-hidden="true" />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.key}>
                  <td className="name-cell">
                    {cut === 'DEAL' ? <Link to={`/deals/${r.key}`}>{r.label}</Link>
                      : cut === 'CUSTOMER' || (cut === 'SUBCONTRACTOR' && r.key !== NONE)
                        ? <Link to={`/counterparties/${r.key}`}>{label(r)}</Link> : label(r)}
                    {r.sub && <span className="sub">{r.sub}</span>}
                  </td>
                  <td className="num">{r.deals}</td>
                  <td className="num">{money(r.price, report.currency)}</td>
                  <td className="num">{money(r.cost, report.currency)}</td>
                  <td className={`num${r.margin < 0 ? ' neg' : ''}`}><b>{money(r.margin, report.currency)}</b></td>
                  <td className={`num${r.margin < 0 ? ' neg' : ''}`}>{pctText(r.marginPct)}</td>
                  <td className="an-bar-col" aria-hidden="true">
                    <span className={`an-bar${r.margin < 0 ? ' neg' : ''}`} style={{ width: `${(Math.abs(r.margin) / peak) * 100}%` }} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}

function HealthSections({ health }: { health: NonNullable<Awaited<ReturnType<typeof api.analyticsHealth>>> }) {
  const { t, money, day } = useI18n();
  const checkText = useCheckText();
  return (
    <>
      <section className="section" aria-label={t('an.sites')}>
        <h2>{t('an.sites')}</h2>
        <p className="lead">{t('an.sitesLead')}</p>
        <div className="panel table-wrap">
          {health.sites.length === 0 ? <p className="empty">{t('an.noSites')}</p> : (
            <table>
              <thead>
                <tr>
                  <th>{t('an.col.site')}</th>
                  <th className="num">{t('an.col.progress')}</th>
                  <th className="num">{t('an.col.late')}</th>
                  <th className="num">{t('an.col.defects')}</th>
                  <th className="num">{t('an.col.pending')}</th>
                </tr>
              </thead>
              <tbody>
                {health.sites.map((s) => (
                  <tr key={s.siteId}>
                    <td className="name-cell"><Link to={`/sites/${s.siteId}`}>{s.name}</Link>
                      <span className="sub">{[s.dealNumber, s.city, s.plannedEnd && t('an.until', { date: day(s.plannedEnd) })]
                        .filter(Boolean).join(' · ')}</span></td>
                    <td className="num">{s.progressPct} %</td>
                    <td className={`num${s.lateTasks ? ' warn-cell' : ''}`}>{s.lateTasks || '—'}</td>
                    <td className={`num${s.overdueDefects ? ' risk-cell' : s.openDefects ? ' warn-cell' : ''}`}>
                      {s.openDefects ? s.openDefects : '—'}
                      {s.overdueDefects > 0 && <span className="sub">{t('an.overdue', { n: s.overdueDefects })}</span>}
                    </td>
                    <td className={`num${s.pendingChangeOrders ? ' warn-cell' : ''}`}>
                      {s.pendingChangeOrders ? <>{s.pendingChangeOrders}
                        <span className="sub">{money(s.pendingChangePrice, s.currency)}</span></> : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <section className="section an-risks" aria-label={t('an.risks')}>
        <h2>{t('an.risks')}</h2>
        <div className="an-risk-grid">
          <div className="panel">
            <h3>{t('an.parties')}</h3>
            <p className="sub">{t('an.partiesLead')}</p>
            {health.counterparties.length === 0 ? <p className="empty">{t('an.noParties')}</p> : (
              <ul className="attention">
                {health.counterparties.map((p) => (
                  <li key={p.id}>
                    <span><Link to={`/counterparties/${p.id}`}>{p.name}</Link>
                      <span className="sub">{p.roles.map((r) => t(`ev.role.${r}`)).join(', ')} · {p.deals.join(', ')}</span></span>
                    <Status status={p.status} />
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="panel">
            <h3>{t('an.packages')}</h3>
            <p className="sub">{t('an.packagesLead')}</p>
            {health.packages.length === 0 ? <p className="empty">{t('an.noPackages')}</p> : (
              <ul className="attention">
                {health.packages.map((p) => (
                  <li key={p.dealId}>
                    <span><Link to={`/deals/${p.dealId}`}>{p.number}</Link> {p.title}
                      <span className="sub">{p.gaps.slice(0, 2).map(checkText).join('; ')}
                        {p.gaps.length > 2 ? ` ${t('an.more', { n: p.gaps.length - 2 })}` : ''}</span></span>
                    <span className="an-counts">
                      {p.missing > 0 && <span className="ev-level MISSING">{t('an.missing', { n: p.missing })}</span>}
                      {p.warn > 0 && <span className="ev-level WARN">{t('an.warn', { n: p.warn })}</span>}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </section>
    </>
  );
}
