import { useRef, useState } from 'react';
import { api, type Deal, type DealDocument, type EvidenceCheck, type EvidenceItem, type EvidenceSection } from '../api';
import { useI18n, type MessageKey } from '../i18n';
import { can } from '../permissions';
import { LoadError } from './LoadError';
import { useAction } from './useAction';
import { useLoad } from './useLoad';

const SECTIONS: EvidenceSection[] = ['CONTRACT', 'QUOTES', 'CHANGE_ORDERS', 'ACCEPTANCE', 'INVOICES', 'DIARY', 'DEFECTS',
  'CHECKS', 'AUDIT'];
const MONEY = new Set(['accepted', 'contract']);
const DAYS = new Set(['date', 'due']);

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

/** The deal's evidence package: what is missing, the contract papers, what the archive will hold, and the download. */
export function DealEvidence({ deal }: { deal: Deal }) {
  const { t, date, day, money } = useI18n();
  const overview = useLoad(() => api.evidence(deal.id), [deal.id, deal.status]);
  const docs = useLoad(() => api.dealDocuments(deal.id), [deal.id]);
  const exporting = useAction();
  const [showOk, setShowOk] = useState(false);
  const upload = useRef<HTMLInputElement>(null);
  const canDocs = can('deals.documents');

  const tr = (key: string, vars?: Record<string, string | number>) => {
    const text = t(key as MessageKey, vars);
    return text === key ? null : text;
  };
  const checkText = (c: EvidenceCheck) => {
    const vars: Record<string, string> = {};
    Object.entries(c.params).forEach(([k, v]) => {
      vars[k] = MONEY.has(k) ? money(Number(v), c.params.currency || null)
        : DAYS.has(k) ? day(v)
        : k === 'status' ? (tr(`status.${v}`) ?? v)
        : k === 'role' ? (tr(`ev.role.${v}`) ?? v)
        : v;
    });
    return tr(`ev.check.${c.code}.${c.level}`, vars) ?? `${c.code} (${c.level})`;
  };

  const download = async () => {
    const res = await exporting.run(() => api.exportEvidence(deal.id));
    if (!res) return;
    saveBlob(res.blob, res.fileName);
    overview.reload();
  };

  const o = overview.data;
  const issues = o?.checks.filter((c) => c.level !== 'OK') ?? [];
  const fine = o?.checks.filter((c) => c.level === 'OK') ?? [];
  const total = o ? o.checks.length : 0;

  return (
    <section className="section evidence" aria-label={t('ev.title')}>
      <div className="section-head">
        <div>
          <h2>{t('ev.title')}</h2>
          <p className="lead">{t('ev.lead')}</p>
        </div>
        <div className="moves">
          <button className="btn primary" onClick={download} disabled={exporting.busy || !o}>
            {exporting.busy ? t('ev.building') : t('ev.download')}
          </button>
        </div>
      </div>
      {exporting.error && <p className="note bad" role="alert">{exporting.error}</p>}
      {overview.error ? <LoadError message={overview.error} onRetry={overview.reload} /> : !o ? (
        <p className="empty" aria-busy="true">{t('loading')}</p>
      ) : (
        <>
          <div className="panel ev-checks">
            <div className="ev-meter" role="img" aria-label={t('ev.score', { ok: o.score.ok, total })}>
              {o.checks.map((c, i) => <span key={i} className={`seg ${c.level}`} />)}
            </div>
            <p className="ev-score"><b>{t('ev.score', { ok: o.score.ok, total })}</b></p>
            <ul className="ev-list">
              {[...issues, ...(showOk ? fine : [])].map((c, i) => (
                <li key={`${c.code}-${i}`} className={c.level}>
                  <span className={`ev-level ${c.level}`}>{t(`ev.level.${c.level}`)}</span>
                  <span className="ev-text">{checkText(c)}</span>
                  {c.code === 'CONTRACT_ATTACHED' && c.level !== 'OK' && canDocs && (
                    <button className="btn small" onClick={() => upload.current?.click()}>{t('ev.attach')}</button>
                  )}
                </li>
              ))}
            </ul>
            {fine.length > 0 && (
              <button className="linkish" onClick={() => setShowOk(!showOk)} aria-expanded={showOk}>
                {showOk ? t('ev.hideOk') : t('ev.showOk', { n: fine.length })}
              </button>
            )}
          </div>

          <ContractDocs deal={deal} docs={docs.data ?? []} error={docs.error} input={upload} editable={canDocs}
            onChange={() => { docs.reload(); overview.reload(); }} />

          <div className="panel ev-contents">
            <h3>{t('ev.contents')}</h3>
            {SECTIONS.map((s) => <SectionRow key={s} section={s} items={o.items.filter((i) => i.section === s)} />)}
          </div>

          <p className="note ev-last">
            {o.lastExport
              ? t('ev.lastExport', { date: date(o.lastExport.at), by: o.lastExport.by ?? '—',
                hash: o.lastExport.sha256sums ? `${o.lastExport.sha256sums.slice(0, 12)}…` : '—' })
              : t('ev.never')}
          </p>
        </>
      )}
    </section>
  );
}

function SectionRow({ section, items }: { section: EvidenceSection; items: EvidenceItem[] }) {
  const { t, day, money } = useI18n();
  const files = items.reduce((n, i) => n + i.files.length, 0);
  const label = t(`ev.section.${section}`);
  if (!items.length) {
    return <div className="ev-row empty-row"><span>{label}</span><span className="sub">{t('ev.empty')}</span></div>;
  }
  return (
    <details className="ev-row">
      <summary><span>{label}</span><span className="sub">{t('ev.files', { n: files })}</span></summary>
      <ul>
        {items.map((i, n) => (
          <li key={n}>
            <div className="ev-item">
              <span className="ev-date">{i.date ? day(i.date) : ''}</span>
              <span className="ev-title">{i.title || (i.text ?? '').split('\n')[0]}
                {i.by && <span className="sub"> · {i.by}</span>}</span>
              {i.amount != null && <span className="num">{money(i.amount, i.currency)}</span>}
            </div>
            {i.files.map((f) => (
              <div key={f.path} className="ev-file">
                <code>{f.path}</code>
                <span className="sub">{f.generated ? t('ev.rendered') : f.sha256 ? `sha256 ${f.sha256.slice(0, 12)}…` : ''}</span>
              </div>
            ))}
          </li>
        ))}
      </ul>
    </details>
  );
}

function ContractDocs({ deal, docs, error, input, editable, onChange }: {
  deal: Deal; docs: DealDocument[]; error: string | null; input: React.RefObject<HTMLInputElement | null>;
  editable: boolean; onChange(): void;
}) {
  const { t, date } = useI18n();
  const action = useAction();
  const [confirm, setConfirm] = useState<string | null>(null);

  const add = async (field: HTMLInputElement) => {
    const list = Array.from(field.files ?? []);
    field.value = '';
    if (!list.length) return;
    for (const f of list) {
      await action.run(() => api.uploadDealDocument(deal.id, f, f.name));
    }
    onChange();
  };
  const open = async (d: DealDocument) => {
    const blob = await action.run(() => api.dealDocumentBlob(deal.id, d.id));
    if (blob) window.open(URL.createObjectURL(blob), '_blank', 'noopener');
  };
  const remove = async (d: DealDocument) => {
    if (confirm !== d.id) { setConfirm(d.id); return; }
    setConfirm(null);
    await action.run(() => api.removeDealDocument(deal.id, d.id));
    onChange();
  };

  return (
    <div className="panel ev-docs">
      <div className="ev-docs-head">
        <div>
          <h3>{t('ev.docs.title')}</h3>
          <p className="sub">{t('ev.docs.lead')}</p>
        </div>
        {editable && (
          <label className={`btn small${action.busy ? ' busy' : ''}`}>
            {action.busy ? t('ev.docs.uploading') : t('ev.docs.upload')}
            <input ref={input} type="file" accept="application/pdf,image/jpeg,image/png,image/webp" multiple hidden
              onChange={(e) => add(e.currentTarget)} />
          </label>
        )}
      </div>
      {(action.error || error) && <p className="note bad" role="alert">{action.error ?? error}</p>}
      {docs.length === 0 ? <p className="empty">{t('ev.docs.empty')}</p> : (
        <ul className="attention">
          {docs.map((d) => (
            <li key={d.id}>
              <span>
                <button className="linkish" onClick={() => open(d)}>{d.fileName}</button>
                <span className="sub">{Math.max(1, Math.round(d.sizeBytes / 1024))} KB · {date(d.uploadedAt)}
                  {d.uploadedBy ? ` · ${d.uploadedBy}` : ''}</span>
              </span>
              {editable && (
                <button className="btn small" onClick={() => remove(d)} disabled={action.busy}>
                  {confirm === d.id ? t('ev.docs.confirm') : t('ev.docs.remove')}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
