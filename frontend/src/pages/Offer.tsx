import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { api, type OfferPage } from '../api';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { LANGS, useI18n } from '../i18n';
import { parseNum } from '../numbers';

/** Public page a subcontractor opens from the private link; no login, the link itself is the key. */
export function OfferPageView() {
  const { token = '' } = useParams();
  const { t, lang, setLang } = useI18n();
  const page = useLoad(() => api.openOffer(token), [token]);
  const p: OfferPage | null = page.data;

  return (
    <div className="public">
      <header className="public-head">
        <span className="brand"><img src="/brand/sphere.png" alt="" /><span><b>Aleruan</b><small>Industry</small></span></span>
        <div className="side-foot" role="group" aria-label="Language">
          {LANGS.map((l) => (
            <button key={l} aria-pressed={lang === l} onClick={() => setLang(l)}>{l.toUpperCase()}</button>
          ))}
        </div>
      </header>
      <main className="public-main">
        {page.error ? (
          <div className="panel box" role="alert"><h1>{t('offer.invalidTitle')}</h1><p className="lead">{t('offer.invalid')}</p></div>
        ) : !p || page.loading ? <p className="empty" aria-busy="true">{t('loading')}</p>
          : <OfferForm key={token} token={token} page={p} onSaved={page.setData} />}
      </main>
    </div>
  );
}

function OfferForm({ token, page: p, onSaved }: { token: string; page: OfferPage; onSaved(p: OfferPage): void }) {
  const { t, day, money, num } = useI18n();
  const [prices, setPrices] = useState<Record<string, string>>(() =>
    Object.fromEntries(p.items.map((i) => [i.id, p.myPrices[i.id] != null ? String(p.myPrices[i.id]) : ''])));
  const [note, setNote] = useState(p.myNote ?? '');
  const [invalid, setInvalid] = useState<string[]>([]);
  const [sent, setSent] = useState(false);
  const action = useAction();

  const value = (id: string) => parseNum(prices[id] ?? '');
  const total = p.items.reduce((s, i) => s + (value(i.id) >= 0 ? value(i.id) * i.quantity : 0), 0);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const bad = p.items.filter((i) => (prices[i.id] ?? '').trim() !== '' && !(value(i.id) >= 0)).map((i) => i.id);
    setInvalid(bad);
    if (bad.length) return;
    const filled = Object.fromEntries(p.items.filter((i) => value(i.id) >= 0).map((i) => [i.id, value(i.id)]));
    if (!Object.keys(filled).length) { action.setError(t('offer.err.empty')); return; }
    const next = await action.run(() => api.submitOffer(token, filled, note.trim()));
    if (next) { onSaved(next); setSent(true); }
  };

  return (
          <form onSubmit={submit} noValidate>
            <h1>{p.title}</h1>
            <p className="lead">
              {t('offer.for', { company: p.invitedCompany })}{' '}
              {p.deadline && t('offer.deadline', { date: day(p.deadline) })}
            </p>
            {!p.open && <p className="note bad">{t('offer.closed')}</p>}
            {sent && <p className="note ok" role="status">{t('offer.thanks')}</p>}
            {!sent && p.submittedAt && p.open && <p className="note">{t('offer.canUpdate')}</p>}
            <p className="lead">{t('offer.explain', { cur: p.currency ?? '' })}</p>
            <div className="panel table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{t('line.description')}</th>
                    <th className="num">{t('line.quantity')}</th>
                    <th className="num">{t('offer.unitPrice', { cur: p.currency ?? '' })}</th>
                    <th className="num">{t('line.priceTotal')}</th>
                  </tr>
                </thead>
                <tbody>
                  {p.items.map((i) => (
                    <tr key={i.id}>
                      <td className="name-cell">{i.description}</td>
                      <td className="num">{num(i.quantity, 4)} {i.unit}</td>
                      <td className="num">
                        <input className="input price" inputMode="decimal" disabled={!p.open}
                          aria-label={t('offer.priceFor', { what: i.description })}
                          aria-invalid={invalid.includes(i.id)} value={prices[i.id] ?? ''}
                          onChange={(e) => { setPrices((x) => ({ ...x, [i.id]: e.target.value })); setSent(false); }} />
                      </td>
                      <td className="num">{value(i.id) >= 0 ? money(value(i.id) * i.quantity) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr><th colSpan={3}>{t('rfq.total', { cur: p.currency ?? '' })}</th>
                    <td className="num total"><b>{money(total, p.currency)}</b></td></tr>
                </tfoot>
              </table>
            </div>
            {invalid.length > 0 && <span className="err offer-err">{t('offer.err.price')}</span>}
            <div className="field note-field">
              <label htmlFor="o-note">{t('offer.note')}</label>
              <textarea id="o-note" className="input" rows={3} value={note} disabled={!p.open}
                onChange={(e) => setNote(e.target.value)} placeholder={t('offer.noteHint')} />
            </div>
            {action.error && <p className="note bad" role="alert">{action.error}</p>}
            {p.open && (
              <button className="btn primary" disabled={action.busy}>
                {p.submittedAt ? t('offer.update') : t('offer.submit')}
              </button>
            )}
          </form>
  );
}
