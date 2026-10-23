import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, type DocLanguage, type Invoice, type InvoiceEdit, type InvoiceLine, type VatMode } from '../api';
import { LoadError } from '../components/LoadError';
import { InvoiceStatusTag } from '../components/invoiceBits';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { localToday } from '../dates';
import { useI18n } from '../i18n';
import { invoiceAmounts, lineTotal } from '../invoiceMath';
import { parseNum } from '../numbers';
import { can } from '../permissions';

const LANGS: DocLanguage[] = ['CS', 'EN', 'PL', 'DE'];
const MODES: VatMode[] = ['STANDARD', 'REVERSE_CHARGE_EU', 'REVERSE_CHARGE_CZ', 'OUTSIDE_SCOPE'];

/** Opens a stored PDF in a new tab; the tab is opened first so popup blockers allow it. */
async function openPdf(id: string) {
  const tab = window.open('', '_blank');
  const url = URL.createObjectURL(await api.invoiceFile(id));
  if (tab) tab.location.href = url; else window.location.href = url;
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export function InvoiceDetailPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const inv = useLoad(() => api.getInvoice(id), [id]);
  if (inv.error) return <LoadError message={inv.error} onRetry={inv.reload} />;
  const i = inv.data;
  if (!i) return <p className="empty" aria-busy="true">{t('loading')}</p>;
  return i.status === 'DRAFT' && can('invoices.write')
    ? <DraftEditor key={i.id} invoice={i} onSaved={inv.setData} />
    : <IssuedInvoice invoice={i} onChange={inv.setData} />;
}

function Header({ i }: { i: Invoice }) {
  const { t } = useI18n();
  return (
    <>
      <Link to="/invoices" className="crumb">{t('inv.back')}</Link>
      <div className="page-head">
        <div>
          <h1>{i.number ?? t('inv.draftOf', { deal: i.dealNumber ?? '' })}</h1>
          <p className="lead">
            <InvoiceStatusTag status={i.status} overdue={i.overdue} />{' '}
            {i.kind === 'CREDIT_NOTE' && <>{t('inv.creditNote')} </>}
            {i.customer?.legalName}{' · '}
            <Link to={`/deals/${i.dealId}`}>{t('site.deal', { number: i.dealNumber ?? '' })}</Link>
          </p>
        </div>
      </div>
    </>
  );
}

interface EditLine { key: string; description: string; unit: string; quantity: string; unitPrice: string; stageId: string | null }
const toEdit = (l: InvoiceLine): EditLine => ({ key: l.id ?? crypto.randomUUID(), description: l.description, unit: l.unit ?? '',
  quantity: String(l.quantity), unitPrice: String(l.unitPrice), stageId: l.stageId });

function DraftEditor({ invoice: i, onSaved }: { invoice: Invoice; onSaved(i: Invoice): void }) {
  const { t, money, num } = useI18n();
  const navigate = useNavigate();
  const action = useAction();
  const [s, setS] = useState({ language: i.language, vatMode: i.vatMode, vatRate: String(i.vatRate),
    taxableDate: i.taxableDate, dueDate: i.dueDate, note: i.note ?? '' });
  const [lines, setLines] = useState<EditLine[]>(i.lines.map(toEdit));
  const [rate, setRate] = useState('');
  const [askRate, setAskRate] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);

  const parsed = lines.map((l) => ({ ...l, q: parseNum(l.quantity), p: parseNum(l.unitPrice) }));
  const vatRate = parseNum(s.vatRate);
  const amounts = invoiceAmounts(parsed.filter((l) => !isNaN(l.q) && !isNaN(l.p)).map((l) => ({ quantity: l.q, unitPrice: l.p })),
    s.vatMode, isNaN(vatRate) ? 0 : vatRate);
  const setLine = (key: string, k: keyof EditLine, v: string) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, [k]: v } : l)));

  const validate = (): InvoiceEdit | null => {
    const errs: string[] = [];
    if (!lines.length) errs.push(t('inv.err.noLines'));
    parsed.forEach((l, n) => {
      if (!l.description.trim()) errs.push(t('inv.err.lineText', { n: n + 1 }));
      if (isNaN(l.q) || l.q === 0) errs.push(t('inv.err.lineQty', { n: n + 1 }));
      if (isNaN(l.p)) errs.push(t('inv.err.linePrice', { n: n + 1 }));
    });
    if (s.vatMode === 'STANDARD' && !(vatRate >= 0 && vatRate < 100)) errs.push(t('quote.err.rate'));
    if (!s.taxableDate || !s.dueDate || s.dueDate < s.taxableDate) errs.push(t('inv.err.due'));
    setErrors(errs);
    if (errs.length) return null;
    return { language: s.language, vatMode: s.vatMode, vatRate: s.vatMode === 'STANDARD' ? vatRate : 0,
      taxableDate: s.taxableDate, dueDate: s.dueDate, note: s.note.trim() || null,
      lines: parsed.map((l) => ({ description: l.description.trim(), unit: l.unit.trim() || null, quantity: l.q,
        unitPrice: l.p, stageId: l.stageId })) };
  };
  const save = async (e?: FormEvent) => {
    e?.preventDefault();
    const data = validate();
    if (!data) return null;
    const saved = await action.run(() => api.editInvoice(i.id, data));
    if (saved) onSaved(saved);
    return saved ?? null;
  };
  const issue = async () => {
    const manual = askRate ? parseNum(rate) : null;
    if (askRate && !(manual! > 0)) { action.setError(t('inv.err.rate')); return; }
    if (!(await save())) return;
    const issued = await action.run(() => api.issueInvoice(i.id, manual));
    if (issued) onSaved(issued);
    else if (i.currency !== 'CZK') setAskRate(true);
  };
  const remove = async () => {
    if (!confirmDelete) { setConfirmDelete(true); return; }
    const ok = await action.run(async () => { await api.deleteInvoice(i.id); return true; });
    if (ok) navigate('/invoices');
  };

  return (
    <>
      <Header i={i} />
      <form onSubmit={save} noValidate className="invoice-edit">
        <section className="panel box">
          <div className="grid4">
            <div className="field">
              <label htmlFor="i-taxable">{t('inv.taxableDate')}</label>
              <input id="i-taxable" type="date" className="input" value={s.taxableDate}
                onChange={(e) => setS({ ...s, taxableDate: e.target.value })} />
            </div>
            <div className="field">
              <label htmlFor="i-due">{t('inv.dueDate')}</label>
              <input id="i-due" type="date" className="input" value={s.dueDate} onChange={(e) => setS({ ...s, dueDate: e.target.value })} />
            </div>
            <div className="field">
              <label htmlFor="i-lang">{t('quote.language')}</label>
              <select id="i-lang" className="select" value={s.language} onChange={(e) => setS({ ...s, language: e.target.value as DocLanguage })}>
                {LANGS.map((l) => <option key={l} value={l}>{t(`quote.lang.${l}`)}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="i-vat">{t('inv.vat')}</label>
              <select id="i-vat" className="select" value={s.vatMode} onChange={(e) => setS({ ...s, vatMode: e.target.value as VatMode })}>
                {MODES.map((m) => <option key={m} value={m}>{t(`vat.mode.${m}`)}</option>)}
              </select>
            </div>
            {s.vatMode === 'STANDARD' && (
              <div className="field">
                <label htmlFor="i-rate">{t('quote.rate')}</label>
                <input id="i-rate" className="input" inputMode="decimal" value={s.vatRate} onChange={(e) => setS({ ...s, vatRate: e.target.value })} />
              </div>
            )}
          </div>
          <p className="sub">{t('inv.dueHint')}</p>
        </section>

        <section className="section">
          <h2>{t('inv.lines')}</h2>
          <div className="panel table-wrap">
            <table className="lines inv-lines">
              <thead>
                <tr><th>{t('line.description')}</th><th className="num">{t('line.quantity')}</th><th>{t('line.unit')}</th>
                  <th className="num">{t('inv.unitPrice', { cur: i.currency })}</th><th className="num">{t('line.priceTotal')}</th><th /></tr>
              </thead>
              <tbody>
                {parsed.map((l, n) => (
                  <tr key={l.key}>
                    <td><input className="input wide" value={l.description} aria-label={t('inv.lineText', { n: n + 1 })}
                      onChange={(e) => setLine(l.key, 'description', e.target.value)} />
                      {l.stageId && <span className="sub">{t('inv.fromStage')}</span>}</td>
                    <td><input className="input num-in" inputMode="decimal" value={l.quantity} aria-label={t('inv.lineQty', { n: n + 1 })}
                      onChange={(e) => setLine(l.key, 'quantity', e.target.value)} /></td>
                    <td><input className="input unit-in" value={l.unit} aria-label={t('inv.lineUnit', { n: n + 1 })}
                      onChange={(e) => setLine(l.key, 'unit', e.target.value)} /></td>
                    <td><input className="input num-in" inputMode="decimal" value={l.unitPrice} aria-label={t('inv.linePrice', { n: n + 1 })}
                      onChange={(e) => setLine(l.key, 'unitPrice', e.target.value)} /></td>
                    <td className="num">{isNaN(l.q) || isNaN(l.p) ? '—' : money(lineTotal(l.q, l.p))}</td>
                    <td><button type="button" className="linkish danger" aria-label={t('inv.removeLine', { n: n + 1 })}
                      onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))}>{t('line.removeShort')}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="add-line">
              <button type="button" className="btn small" onClick={() => setLines((ls) => [...ls,
                { key: crypto.randomUUID(), description: '', unit: '', quantity: '1', unitPrice: '', stageId: null }])}>
                {t('line.add')}
              </button>
            </div>
          </div>
          <dl className="inv-totals">
            <div><dt>{t('quote.subtotal')}</dt><dd>{money(amounts.subtotal, i.currency)}</dd></div>
            <div><dt>{s.vatMode === 'STANDARD' ? t('quote.vatAmount', { rate: num(isNaN(vatRate) ? 0 : vatRate) }) : t(`vat.mode.${s.vatMode}`)}</dt>
              <dd>{s.vatMode === 'STANDARD' ? money(amounts.vatAmount, i.currency) : '—'}</dd></div>
            <div className="grand"><dt>{t('inv.toPay')}</dt><dd>{money(amounts.total, i.currency)}</dd></div>
          </dl>
        </section>

        <div className="field">
          <label htmlFor="i-note">{t('inv.note')}</label>
          <textarea id="i-note" className="input" rows={2} value={s.note} onChange={(e) => setS({ ...s, note: e.target.value })} />
        </div>
        {i.currency !== 'CZK' && (
          <p className="note">{t('inv.cnbHint', { cur: i.currency })}</p>
        )}
        {askRate && (
          <div className="field narrow">
            <label htmlFor="i-cnb">{t('inv.manualRate', { cur: i.currency })}</label>
            <input id="i-cnb" className="input" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} />
          </div>
        )}
        {errors.length > 0 && <ul className="note bad errs" role="alert">{errors.map((e) => <li key={e}>{e}</li>)}</ul>}
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button type="button" className="btn primary" disabled={action.busy} onClick={issue}>{t('inv.issue')}</button>
          <button className="btn" disabled={action.busy}>{t('inv.saveDraft')}</button>
          <button type="button" className="btn" onClick={remove} disabled={action.busy}>
            {confirmDelete ? t('deal.confirm', { what: t('inv.deleteDraft') }) : t('inv.deleteDraft')}
          </button>
        </div>
        <p className="sub">{t('inv.issueHint')}</p>
      </form>
    </>
  );
}

function IssuedInvoice({ invoice: i, onChange }: { invoice: Invoice; onChange(i: Invoice): void }) {
  const { t, day, money, date, num } = useI18n();
  const navigate = useNavigate();
  const action = useAction();
  const [today] = useState(localToday);
  const rest = Math.round((i.total - i.paidAmount) * 100) / 100;
  const [pay, setPay] = useState({ amount: String(rest), paidOn: today });
  const [credit, setCredit] = useState<string | null>(null);

  const recordPayment = async (e: FormEvent) => {
    e.preventDefault();
    const amount = parseNum(pay.amount);
    if (!(amount > 0)) { action.setError(t('inv.err.amount')); return; }
    if (!pay.paidOn || pay.paidOn > today) { action.setError(t('inv.err.paidOn')); return; }
    const n = await action.run(() => api.payInvoice(i.id, amount, pay.paidOn));
    if (n) { onChange(n); setPay({ amount: String(Math.round((n.total - n.paidAmount) * 100) / 100), paidOn: today }); }
  };
  const makeCredit = async () => {
    if (credit === null) { setCredit(''); return; }
    if (!credit.trim()) { action.setError(t('inv.err.creditReason')); return; }
    const note = await action.run(() => api.creditInvoice(i.id, credit.trim()));
    if (note) navigate(`/invoices/${note.id}`);
  };
  const open = async () => { try { await openPdf(i.id); } catch (e) { action.setError(e instanceof Error ? e.message : String(e)); } };

  return (
    <>
      <Header i={i} />
      <div className="moves page-actions">
        {i.status !== 'DRAFT' && <button className="btn primary" onClick={open}>{t('quote.open')}</button>}
        {can('invoices.write') && i.kind === 'INVOICE' && (i.status === 'ISSUED' || i.status === 'PAID') && (
          <button className="btn" onClick={makeCredit} disabled={action.busy}>
            {credit === null ? t('inv.credit') : t('deal.confirm', { what: t('inv.credit') })}
          </button>
        )}
      </div>
      {credit !== null && (
        <div className="field">
          <label htmlFor="i-credit">{t('inv.creditReason')}</label>
          <input id="i-credit" className="input" value={credit} onChange={(e) => setCredit(e.target.value)} autoFocus />
          <span className="sub">{t('inv.creditHint')}</span>
        </div>
      )}
      {action.error && <p className="note bad" role="alert">{action.error}</p>}

      <dl className="panel facts">
        <div><dt>{t('inv.variableSymbol')}</dt><dd>{i.variableSymbol}</dd></div>
        <div><dt>{t('inv.col.issued')}</dt><dd>{day(i.issueDate)}</dd></div>
        <div><dt>{t('inv.taxableDate')}</dt><dd>{day(i.taxableDate)}</dd></div>
        <div><dt>{t('inv.dueDate')}</dt><dd className={i.overdue ? 'neg' : ''}>{day(i.dueDate)}</dd></div>
        <div><dt>{t('inv.vat')}</dt><dd>{t(`vat.mode.${i.vatMode}`)}{i.vatMode === 'STANDARD' && ` ${num(i.vatRate)} %`}</dd></div>
        {i.exchangeRate != null && (
          <div><dt>{t('inv.cnbRate')}</dt><dd>{t('inv.rateValue', { cur: i.currency, rate: num(i.exchangeRate, 3), date: day(i.rateDate) })}
            {i.vatAmountCzk != null && i.vatMode === 'STANDARD' && <span className="sub">{t('inv.vatCzk', { sum: money(i.vatAmountCzk, 'CZK') })}</span>}</dd></div>
        )}
        <div><dt>{t('inv.issuedBy')}</dt><dd>{i.issuedBy} <span className="sub">{date(i.issuedAt)}</span></dd></div>
        {i.creditOfId && <div><dt>{t('inv.corrects')}</dt><dd><Link to={`/invoices/${i.creditOfId}`}>{t('inv.originalInvoice')}</Link></dd></div>}
      </dl>

      <section className="section">
        <div className="panel table-wrap">
          <table className="lines">
            <thead><tr><th>{t('line.description')}</th><th className="num">{t('line.quantity')}</th>
              <th className="num">{t('inv.unitPrice', { cur: i.currency })}</th><th className="num">{t('line.priceTotal')}</th></tr></thead>
            <tbody>
              {i.lines.map((l) => (
                <tr key={l.id}><td>{l.description}</td><td className="num">{num(l.quantity, 4)} {l.unit}</td>
                  <td className="num">{money(l.unitPrice)}</td><td className="num">{money(l.total ?? 0)}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
        <dl className="inv-totals">
          <div><dt>{t('quote.subtotal')}</dt><dd>{money(i.subtotal, i.currency)}</dd></div>
          <div><dt>{i.vatMode === 'STANDARD' ? t('quote.vatAmount', { rate: num(i.vatRate) }) : t(`vat.mode.${i.vatMode}`)}</dt>
            <dd>{i.vatMode === 'STANDARD' ? money(i.vatAmount, i.currency) : '—'}</dd></div>
          <div className="grand"><dt>{t('inv.toPay')}</dt><dd>{money(i.total, i.currency)}</dd></div>
          {i.paidAmount > 0 && <div><dt>{t('inv.paid')}</dt><dd>{money(i.paidAmount, i.currency)}{i.paidOn && <span className="sub">{day(i.paidOn)}</span>}</dd></div>}
        </dl>
      </section>

      {can('invoices.write') && i.kind === 'INVOICE' && i.status === 'ISSUED' && (
        <form className="panel box pay-form" onSubmit={recordPayment} noValidate>
          <h2>{t('inv.recordPayment')}</h2>
          <div className="grid3">
            <div className="field">
              <label htmlFor="p-amount">{t('inv.amount', { cur: i.currency })}</label>
              <input id="p-amount" className="input" inputMode="decimal" value={pay.amount} onChange={(e) => setPay({ ...pay, amount: e.target.value })} />
            </div>
            <div className="field">
              <label htmlFor="p-date">{t('inv.paidOn')}</label>
              <input id="p-date" type="date" className="input" max={today} value={pay.paidOn} onChange={(e) => setPay({ ...pay, paidOn: e.target.value })} />
            </div>
            <div className="field"><label>&nbsp;</label>
              <button className="btn primary" disabled={action.busy}>{t('inv.markPaid')}</button></div>
          </div>
          <p className="sub">{t('inv.payHint')}</p>
        </form>
      )}
    </>
  );
}
