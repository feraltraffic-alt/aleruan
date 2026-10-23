import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  api, type DocType, type ImportDetail, type ImportOutcome, type ImportReview as Review, type ImportWarning, type PartyRole,
} from '../api';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n, type MessageKey } from '../i18n';
import { ImportStatusTag } from './Import';

const TYPES: DocType[] = ['SUPPLIER_INVOICE', 'CUSTOMER_INVOICE', 'CREDIT_NOTE', 'CONTRACT', 'CMR', 'OTHER'];
const ROLES: PartyRole[] = ['SUBCONTRACTOR', 'SUPPLIER', 'CUSTOMER', 'CARRIER'];
const INVOICES: DocType[] = ['SUPPLIER_INVOICE', 'CUSTOMER_INVOICE', 'CREDIT_NOTE'];

interface Form {
  type: DocType; documentNumber: string; issueDate: string; taxableDate: string; dueDate: string; currency: string;
  variableSymbol: string; netAmount: string; vatAmount: string; totalAmount: string; vatNote: string;
  useMatch: boolean; name: string; country: string; companyId: string; vatNumber: string; address: string;
  bankAccount: string; role: PartyRole | ''; dealId: string;
}

const isoOrEmpty = (s: string | null | undefined) => (s && /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : '');

function formFrom(d: ImportDetail): Form {
  const c = d.confirmed;
  const e = d.read;
  const p = d.suggestion.party;
  if (c) {
    return { type: c.type, documentNumber: c.documentNumber ?? '', issueDate: c.issueDate ?? '', taxableDate: c.taxableDate ?? '',
      dueDate: c.dueDate ?? '', currency: c.currency ?? '', variableSymbol: c.variableSymbol ?? '',
      netAmount: c.netAmount?.toString() ?? '', vatAmount: c.vatAmount?.toString() ?? '', totalAmount: c.totalAmount?.toString() ?? '',
      vatNote: c.vatNote ?? '', useMatch: !!c.counterpartyId, name: c.party?.name ?? '', country: c.party?.country ?? '',
      companyId: c.party?.companyId ?? '', vatNumber: c.party?.vatNumber ?? '', address: c.party?.address ?? '',
      bankAccount: c.party?.bankAccount ?? '', role: c.role ?? '', dealId: c.dealId ?? '' };
  }
  return { type: d.suggestion.type, documentNumber: e?.documentNumber ?? '', issueDate: isoOrEmpty(e?.issueDate),
    taxableDate: isoOrEmpty(e?.taxableDate), dueDate: isoOrEmpty(e?.dueDate), currency: e?.currency ?? '',
    variableSymbol: e?.variableSymbol ?? '', netAmount: e?.netAmount ?? '', vatAmount: e?.vatAmount ?? '',
    totalAmount: e?.totalAmount ?? '', vatNote: e?.vatNote ?? '', useMatch: !!d.suggestion.match, name: p.name,
    country: p.country, companyId: p.companyId, vatNumber: p.vatNumber, address: p.address, bankAccount: p.bankAccount,
    role: d.suggestion.role ?? '', dealId: '' };
}

const amount = (s: string) => {
  if (!s.trim()) return null;
  const v = Number(s.replace(/\s/g, '').replace(',', '.'));
  return Number.isFinite(v) ? v : NaN;
};

/** The document beside what was read from it: correct, then confirm. Nothing is created before that. */
export function ImportReviewPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const detail = useLoad(() => api.getImport(id), [id]);
  const preview = useLoad(() => api.importFile(id), [id]);
  const [outcome, setOutcome] = useState<ImportOutcome | null>(null);

  const reading = detail.data && ['QUEUED', 'PROCESSING'].includes(detail.data.document.status);
  const { reload } = detail;
  useEffect(() => {
    if (!reading) return undefined;
    const timer = setInterval(reload, 3000);
    return () => clearInterval(timer);
  }, [reading, reload]);

  const url = useMemo(() => (preview.data ? URL.createObjectURL(preview.data) : null), [preview.data]);
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);

  if (detail.error) return <LoadError message={detail.error} onRetry={detail.reload} />;
  const d = detail.data;
  if (!d) return <p className="empty" aria-busy="true">{t('loading')}</p>;
  const pdf = d.document.contentType === 'application/pdf';

  return (
    <>
      <Link to="/import" className="crumb">{t('im.back')}</Link>
      <div className="page-head">
        <div>
          <h1>{d.read?.documentNumber || d.document.fileName}</h1>
          <p className="lead"><ImportStatusTag status={d.document.status} /> {d.read?.summary ?? ''}</p>
        </div>
      </div>
      <div className="im-split">
        <div className="panel im-preview">
          {!url ? <p className="empty" aria-busy="true">{t('loading')}</p>
            : pdf ? <iframe src={url} title={d.document.fileName} />
              : <img src={url} alt={d.document.fileName} />}
          <a className="sub" href={url ?? undefined} target="_blank" rel="noreferrer">{t('im.openOriginal', { name: d.document.fileName })}</a>
        </div>
        <ReviewForm key={`${d.document.id}-${d.document.status}`} detail={d} onChanged={detail.reload}
          outcome={outcome} onOutcome={setOutcome} />
      </div>
    </>
  );
}

function ReviewForm({ detail, onChanged, outcome, onOutcome }: {
  detail: ImportDetail; onChanged(): void; outcome: ImportOutcome | null; onOutcome(o: ImportOutcome): void;
}) {
  const { t, date, num } = useI18n();
  const navigate = useNavigate();
  const action = useAction();
  const [f, setF] = useState<Form>(() => formFrom(detail));
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const deals = useLoad(() => api.listDeals(), []);
  const doc = detail.document;
  const open = ['QUEUED', 'EXTRACTED', 'FAILED'].includes(doc.status);
  const match = detail.suggestion.match;
  const set = (patch: Partial<Form>) => setF((x) => ({ ...x, ...patch }));
  const invoice = INVOICES.includes(f.type);
  const needsParty = invoice || f.type === 'CONTRACT' || f.type === 'CMR';
  const usingMatch = f.useMatch && !!match;

  const warningText = (w: ImportWarning) => {
    const key = `im.warn.${w.code}` as MessageKey;
    const vars = { ...w.params, field: w.params.field ? t(`im.field.${w.params.field}` as MessageKey) : '' };
    const text = t(key, vars);
    return text === key ? w.code : text;
  };

  const review = (): Review | string => {
    const nums = { net: amount(f.netAmount), vat: amount(f.vatAmount), total: amount(f.totalAmount) };
    if (Object.values(nums).some((v) => Number.isNaN(v))) return t('im.err.amount');
    if (invoice && (!f.documentNumber.trim() || nums.total === null || !f.currency.trim())) return t('im.err.invoice');
    if (needsParty && !usingMatch && (!f.name.trim() || !/^[A-Za-z]{2}$/.test(f.country.trim()))) return t('im.err.party');
    const party = usingMatch || !f.name.trim() ? (f.bankAccount.trim() ? { name: f.name.trim() || match!.legalName,
      country: f.country.trim().toUpperCase() || match!.country, companyId: null, vatNumber: null, address: null,
      bankAccount: f.bankAccount.trim() } : null)
      : { name: f.name.trim(), country: f.country.trim().toUpperCase(), companyId: f.companyId.trim() || null,
        vatNumber: f.vatNumber.trim() || null, address: f.address.trim() || null, bankAccount: f.bankAccount.trim() || null };
    return { type: f.type, documentNumber: f.documentNumber.trim() || null, issueDate: f.issueDate || null,
      taxableDate: f.taxableDate || null, dueDate: f.dueDate || null, currency: f.currency.trim().toUpperCase() || null,
      variableSymbol: f.variableSymbol.trim() || null, netAmount: nums.net, vatAmount: nums.vat, totalAmount: nums.total,
      vatNote: f.vatNote.trim() || null, party, counterpartyId: usingMatch ? match!.id : null, role: f.role || null,
      dealId: f.dealId || null };
  };

  const confirm = async () => {
    const r = review();
    if (typeof r === 'string') { action.setError(r); return; }
    const res = await action.run(() => api.confirmImport(doc.id, r));
    if (res) { onOutcome(res); onChanged(); }
  };
  const reject = async () => {
    if (!rejecting) { setRejecting(true); return; }
    const res = await action.run(() => api.rejectImport(doc.id, reason.trim() || null));
    if (res) navigate('/import');
  };
  const retry = async () => {
    const res = await action.run(() => api.retryImport(doc.id));
    if (res) onChanged();
  };

  const field = (key: keyof Form, label: string, type = 'text', extra: Partial<React.InputHTMLAttributes<HTMLInputElement>> = {}) => (
    <label className="field">
      <span>{label}</span>
      <input className="input" type={type} value={String(f[key] ?? '')} disabled={!open}
        onChange={(e) => set({ [key]: e.target.value } as Partial<Form>)} {...extra} />
    </label>
  );

  return (
    <div className="panel im-form">
      {doc.status === 'QUEUED' || doc.status === 'PROCESSING' ? <p className="note">{t('im.reading')}</p> : null}
      {doc.status === 'FAILED' && <p className="note bad">{t('im.failed', { error: doc.error ?? '' })}</p>}
      {doc.status === 'REJECTED' && <p className="note">{t('im.rejected', { reason: doc.error ?? '—', by: doc.decidedBy ?? '' })}</p>}
      {doc.status === 'CONFIRMED' && !outcome && <p className="note ok">{t('im.confirmedBy', { by: doc.decidedBy ?? '', date: date(doc.decidedAt) })}</p>}
      {outcome && <OutcomeNote outcome={outcome} dealId={f.dealId} />}
      {detail.suggestion.duplicateInvoice && open && <p className="note bad">{t('im.duplicateInvoice')}</p>}
      {detail.warnings.length > 0 && open && (
        <div className="im-warnings">
          <h3>{t('im.checkThese')}</h3>
          <ul>{detail.warnings.map((w, i) => <li key={i}>{warningText(w)}</li>)}</ul>
        </div>
      )}

      <fieldset disabled={!open}>
        <label className="field">
          <span>{t('im.col.type')}</span>
          <select className="select" value={f.type} onChange={(e) => {
            const type = e.target.value as DocType;
            set({ type, role: ({ SUPPLIER_INVOICE: 'SUBCONTRACTOR', CUSTOMER_INVOICE: 'CUSTOMER', CONTRACT: 'CUSTOMER',
              CMR: 'CARRIER' } as Partial<Record<DocType, PartyRole>>)[type] ?? f.role });
          }}>
            {TYPES.map((x) => <option key={x} value={x}>{t(`im.type.${x}`)}</option>)}
          </select>
        </label>

        <h3>{t('im.party')}</h3>
        {match && (
          <div className="im-match">
            <label className="toggle">
              <input type="checkbox" checked={f.useMatch} onChange={(e) => set({ useMatch: e.target.checked })} />
              {t('im.useMatch')} <Link to={`/counterparties/${match.id}`}>{match.legalName}</Link> <Status status={match.checkStatus} />
            </label>
          </div>
        )}
        {!usingMatch && (
          <div className="grid2">
            {field('name', t('im.f.name'))}
            {field('country', t('im.f.country'), 'text', { maxLength: 2 })}
            {field('companyId', t('im.f.companyId'))}
            {field('vatNumber', t('im.f.vatNumber'))}
            {field('address', t('im.f.address'))}
          </div>
        )}
        <div className="grid2">
          {field('bankAccount', t('im.f.bankAccount'))}
          <label className="field">
            <span>{t('im.f.role')}</span>
            <select className="select" value={f.role} onChange={(e) => set({ role: e.target.value as PartyRole | '' })}>
              <option value="">{t('im.f.noRole')}</option>
              {ROLES.map((r) => <option key={r} value={r}>{t(`im.role.${r}`)}</option>)}
            </select>
          </label>
        </div>
        {!usingMatch && !match && f.name.trim() && <p className="sub">{t('im.newParty')}</p>}

        <h3>{t('im.document')}</h3>
        <div className="grid2">
          {field('documentNumber', t('im.f.number'))}
          {field('variableSymbol', t('im.f.vs'))}
          {field('issueDate', t('im.f.issueDate'), 'date')}
          {field('taxableDate', t('im.f.taxableDate'), 'date')}
          {field('dueDate', t('im.f.dueDate'), 'date')}
          {field('currency', t('im.f.currency'), 'text', { maxLength: 3 })}
          {field('netAmount', t('im.f.net'), 'text', { inputMode: 'decimal' })}
          {field('vatAmount', t('im.f.vat'), 'text', { inputMode: 'decimal' })}
          {field('totalAmount', t('im.f.total'), 'text', { inputMode: 'decimal' })}
        </div>
        {field('vatNote', t('im.f.vatNote'))}
        <label className="field">
          <span>{t('im.f.deal')}</span>
          <select className="select" value={f.dealId} onChange={(e) => set({ dealId: e.target.value })}>
            <option value="">{t('im.f.noDeal')}</option>
            {deals.data?.map((x) => <option key={x.id} value={x.id}>{x.number} · {x.title}</option>)}
          </select>
        </label>
        {f.type === 'CONTRACT' && <p className="sub">{t('im.contractHint')}</p>}
      </fieldset>

      {detail.read && detail.read.lines.length > 0 && (
        <details className="im-lines">
          <summary>{t('im.lines', { n: detail.read.lines.length })}</summary>
          <table>
            <tbody>
              {detail.read.lines.map((l, i) => (
                <tr key={i}><td>{l.description}</td><td className="num">{l.quantity} {l.unit}</td>
                  <td className="num">{l.unitPrice}</td><td className="num">{l.total}</td></tr>
              ))}
            </tbody>
          </table>
        </details>
      )}

      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      {open && (
        <div className="actions">
          <button className="btn primary" onClick={confirm} disabled={action.busy || doc.status === 'PROCESSING'}>{t('im.confirm')}</button>
          {rejecting && <input className="input" placeholder={t('im.rejectReason')} value={reason} aria-label={t('im.rejectReason')}
            onChange={(e) => setReason(e.target.value)} />}
          <button className="btn" onClick={reject} disabled={action.busy}>{rejecting ? t('im.rejectConfirm') : t('im.reject')}</button>
          {(doc.status === 'FAILED' || doc.status === 'EXTRACTED') && (
            <button className="btn" onClick={retry} disabled={action.busy}>{t('im.retry')}</button>
          )}
        </div>
      )}
      {doc.model && (
        <p className="sub im-meta">{t('im.readBy', { model: doc.model, date: date(doc.processedAt),
          tokens: num((doc.inputTokens ?? 0) + (doc.outputTokens ?? 0), 0) })}</p>
      )}
    </div>
  );
}

function OutcomeNote({ outcome, dealId }: { outcome: ImportOutcome; dealId: string }) {
  const { t } = useI18n();
  return (
    <div className="note ok" role="status">
      {t(`im.outcome.${outcome.resultType}` as MessageKey)}{' '}
      {outcome.resultType === 'SUPPLIER_INVOICE' && <Link to="/invoices?tab=received">{t('im.openReceived')}</Link>}
      {outcome.resultType === 'DEAL_DOCUMENT' && dealId && <Link to={`/deals/${dealId}`}>{t('im.openDeal')}</Link>}
      {outcome.counterpartyId && <> · <Link to={`/counterparties/${outcome.counterpartyId}`}>
        {outcome.counterpartyCreated ? t('im.partyCreated') : t('im.partyKnown')}</Link></>}
    </div>
  );
}
