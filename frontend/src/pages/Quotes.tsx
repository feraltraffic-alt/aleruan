import { useState, type FormEvent } from 'react';
import { api, type Deal, type IssueQuote, type QuoteLanguage, type VatMode } from '../api';
import { Drawer } from '../components/Drawer';
import { LoadError } from '../components/LoadError';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { parseNum } from '../numbers';
import { vatAmount } from '../vat';

const LANGUAGES: QuoteLanguage[] = ['CS', 'EN', 'PL', 'DE'];
const MODES: VatMode[] = ['STANDARD', 'REVERSE_CHARGE_EU', 'REVERSE_CHARGE_CZ', 'OUTSIDE_SCOPE'];

/** Opens the stored quote file in a new tab; the tab is opened first so popup blockers allow it. */
async function openQuote(id: string) {
  const tab = window.open('', '_blank');
  const blob = await api.quoteFile(id);
  const url = URL.createObjectURL(blob);
  if (tab) tab.location.href = url; else window.location.href = url;
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export function QuotesSection({ deal, onIssued }: { deal: Deal; onIssued(): void }) {
  const { t, day, money } = useI18n();
  const quotes = useLoad(() => api.listQuotes(deal.id), [deal.id]);
  const [issuing, setIssuing] = useState(false);
  const [openError, setOpenError] = useState<string | null>(null);
  const [today] = useState(() => new Date().toISOString().slice(0, 10));

  const open = async (id: string) => {
    setOpenError(null);
    try { await openQuote(id); } catch (e) { setOpenError(e instanceof Error ? e.message : String(e)); }
  };

  return (
    <section className="section">
      <div className="section-head">
        <div>
          <h2>{t('quote.title')}</h2>
          <p className="lead">{t('quote.lead')}</p>
        </div>
        {deal.pricesEditable && deal.lines.length > 0 && (
          <button className="btn primary" onClick={() => setIssuing(true)}>{t('quote.issue')}</button>
        )}
      </div>
      {openError && <p className="note bad" role="alert">{openError}</p>}
      {quotes.error ? <LoadError message={quotes.error} onRetry={quotes.reload} /> : (
        <div className="panel table-wrap">
          {quotes.data?.length === 0 ? <p className="empty">{deal.lines.length ? t('quote.empty') : t('quote.needLines')}</p> : (
            <table>
              <thead>
                <tr>
                  <th>{t('quote.col.number')}</th><th>{t('quote.col.issued')}</th><th>{t('quote.col.valid')}</th>
                  <th>{t('quote.col.vat')}</th><th className="num">{t('quote.col.total')}</th><th />
                </tr>
              </thead>
              <tbody>
                {quotes.data?.map((q, i) => (
                  <tr key={q.id} className={i > 0 ? 'superseded' : ''}>
                    <td className="name-cell">
                      {q.number}
                      <span className="sub">{t(`quote.lang.${q.language}`)}{i > 0 ? `, ${t('quote.superseded')}` : ''}</span>
                    </td>
                    <td>{day(q.issuedOn)}<span className="sub">{q.issuedBy}</span></td>
                    <td>{day(q.validUntil)}{q.validUntil < today && <span className="sub neg">{t('quote.expired')}</span>}</td>
                    <td>{t(`vat.mode.${q.vatMode}`)}{q.vatMode === 'STANDARD' && ` ${q.vatRate} %`}</td>
                    <td className="num">{money(q.total, q.currency)}</td>
                    <td><button className="linkish" onClick={() => open(q.id)}>{t('quote.open')}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      {issuing && (
        <QuoteForm deal={deal} onClose={() => setIssuing(false)}
          onIssued={async (id) => { setIssuing(false); await quotes.reload(); onIssued(); void open(id); }} />
      )}
    </section>
  );
}

function QuoteForm({ deal, onClose, onIssued }: { deal: Deal; onClose(): void; onIssued(id: string): void }) {
  const { t, money, num } = useI18n();
  const defaults = useLoad(() => api.quoteDefaults(deal.id), [deal.id]);
  const action = useAction();
  const [choice, setChoice] = useState<{ language?: QuoteLanguage; vatMode?: VatMode; rate?: string; days?: string }>({});
  const [terms, setTerms] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  if (defaults.error) {
    return <Drawer title={t('quote.issue')} onClose={onClose}><LoadError message={defaults.error} onRetry={defaults.reload} /></Drawer>;
  }
  const d = defaults.data;
  const language = choice.language ?? d?.language ?? 'EN';
  const mode = choice.vatMode ?? d?.vatMode ?? 'STANDARD';
  const rateText = choice.rate ?? String(d?.vatMode === 'STANDARD' ? d.vatRate : 21);
  const daysText = choice.days ?? String(d?.validDays ?? 30);
  const rate = parseNum(rateText);
  const vat = mode === 'STANDARD' && rate >= 0 ? vatAmount(deal.totals.price, mode, rate) : 0;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    const days = parseNum(daysText);
    if (!(Number.isInteger(days) && days >= 1 && days <= 365)) errs.days = t('quote.err.days');
    if (mode === 'STANDARD' && !(rate >= 0 && rate < 100)) errs.rate = t('quote.err.rate');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const input: IssueQuote = { language, validDays: days, vatMode: mode, vatRate: mode === 'STANDARD' ? rate : null,
      terms: terms.trim() || null };
    const q = await action.run(() => api.issueQuote(deal.id, input));
    if (q) onIssued(q.id);
  };

  return (
    <Drawer title={t('quote.issue')} onClose={onClose}>
      {!d ? <p className="empty" aria-busy="true">{t('loading')}</p> : (
        <form onSubmit={submit} noValidate>
          <div className="grid2">
            <div className="field">
              <label htmlFor="q-lang">{t('quote.language')}</label>
              <select id="q-lang" className="select" value={language}
                onChange={(e) => setChoice((c) => ({ ...c, language: e.target.value as QuoteLanguage }))}>
                {LANGUAGES.map((l) => <option key={l} value={l}>{t(`quote.lang.${l}`)}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="q-days">{t('quote.validDays')}</label>
              <input id="q-days" className="input" inputMode="numeric" value={daysText} aria-invalid={!!errors.days}
                onChange={(e) => { setChoice((c) => ({ ...c, days: e.target.value })); setErrors((x) => ({ ...x, days: '' })); }} />
              {errors.days && <span className="err">{errors.days}</span>}
            </div>
          </div>
          <fieldset className="field">
            <legend>{t('quote.vat')}</legend>
            <div className="picklist">
              {MODES.map((m) => (
                <label key={m}>
                  <input type="radio" name="vat" checked={mode === m}
                    onChange={() => setChoice((c) => ({ ...c, vatMode: m }))} />
                  <span>
                    {t(`vat.mode.${m}`)}{m === d.vatMode && <span className="pill d-QUOTED suggested">{t('quote.suggested')}</span>}
                    <span className="sub">{t(`vat.hint.${m}`)}</span>
                  </span>
                </label>
              ))}
            </div>
            <span className="sub reason">{t(`vat.reason.${d.reason}`)} {t('quote.notAdvice')}</span>
          </fieldset>
          {mode === 'STANDARD' && (
            <div className="field narrow">
              <label htmlFor="q-rate">{t('quote.rate')}</label>
              <input id="q-rate" className="input" inputMode="decimal" value={rateText} aria-invalid={!!errors.rate}
                onChange={(e) => { setChoice((c) => ({ ...c, rate: e.target.value })); setErrors((x) => ({ ...x, rate: '' })); }} />
              {errors.rate && <span className="err">{errors.rate}</span>}
            </div>
          )}
          <div className="field">
            <label htmlFor="q-terms">{t('quote.terms')}</label>
            <textarea id="q-terms" className="input" rows={3} value={terms} placeholder={t('quote.termsHint')}
              onChange={(e) => setTerms(e.target.value)} />
          </div>
          <dl className="preview" aria-live="polite">
            <div><dt>{t('quote.subtotal')}</dt><dd>{money(deal.totals.price, deal.currency)}</dd></div>
            <div><dt>{mode === 'STANDARD' ? t('quote.vatAmount', { rate: num(rate) }) : t('quote.noVat')}</dt>
              <dd>{mode === 'STANDARD' ? money(vat, deal.currency) : '—'}</dd></div>
            <div><dt>{t('quote.total')}</dt><dd>{money(Math.round((deal.totals.price + vat) * 100) / 100, deal.currency)}</dd></div>
          </dl>
          <p className="note">{deal.status === 'DRAFT' ? t('quote.willQuote') : t('quote.newVersion')}</p>
          {action.error && <p className="note bad" role="alert">{action.error}</p>}
          <div className="actions">
            <button className="btn primary" disabled={action.busy}>{t('quote.issueNow')}</button>
            <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
          </div>
        </form>
      )}
    </Drawer>
  );
}
