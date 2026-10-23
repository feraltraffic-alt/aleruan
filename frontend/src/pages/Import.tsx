import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, type ImportDoc, type ImportStatus } from '../api';
import { LoadError } from '../components/LoadError';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';

type Tab = 'review' | 'queue' | 'failed' | 'done' | 'rejected' | 'all';
const TABS: Tab[] = ['review', 'queue', 'failed', 'done', 'rejected', 'all'];
const IN_TAB: Record<Tab, ImportStatus[] | null> = {
  review: ['EXTRACTED'], queue: ['QUEUED', 'PROCESSING'], failed: ['FAILED'], done: ['CONFIRMED'], rejected: ['REJECTED'],
  all: null,
};

export function ImportStatusTag({ status }: { status: ImportStatus }) {
  const { t } = useI18n();
  return <span className={`pill im-${status}`}>{t(`im.status.${status}`)}</span>;
}

/** Upload scans and PDFs; Claude reads them; a person checks each one before anything is created. */
export function ImportPage() {
  const { t, date, money } = useI18n();
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('review');
  const list = useLoad(() => api.listImports(), []);
  const queue = useLoad(() => api.importQueue(), []);
  const [uploading, setUploading] = useState<{ done: number; total: number } | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [duplicates, setDuplicates] = useState<string[]>([]);
  const [dragging, setDragging] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const waiting = (list.data ?? []).some((d) => d.status === 'QUEUED' || d.status === 'PROCESSING');
  const { reload: reloadList } = list;
  const { reload: reloadQueue } = queue;
  useEffect(() => {
    if (!waiting) return undefined;
    const timer = setInterval(() => { reloadList(); reloadQueue(); }, 4000);
    return () => clearInterval(timer);
  }, [waiting, reloadList, reloadQueue]);

  const upload = async (files: File[]) => {
    if (!files.length) return;
    setErrors([]);
    setDuplicates([]);
    setUploading({ done: 0, total: files.length });
    const failed: string[] = [];
    const dup: string[] = [];
    for (const [i, f] of files.entries()) {
      try {
        const res = await api.uploadImport(f, f.name);
        if (res.duplicate) dup.push(f.name);
      } catch (e) {
        failed.push(`${f.name}: ${e instanceof Error ? e.message : String(e)}`);
      }
      setUploading({ done: i + 1, total: files.length });
    }
    setUploading(null);
    setErrors(failed);
    setDuplicates(dup);
    setTab('queue');
    list.reload();
    queue.reload();
  };

  const docs = (list.data ?? []).filter((d) => !IN_TAB[tab] || IN_TAB[tab]!.includes(d.status));
  const count = (k: Tab) => (list.data ?? []).filter((d) => !IN_TAB[k] || IN_TAB[k]!.includes(d.status)).length;
  const q = queue.data;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t('im.title')}</h1>
          <p className="lead">{t('im.lead')}</p>
        </div>
      </div>

      {q && (
        <p className={`note${q.readerConfigured ? '' : ' warn'}`}>
          {q.readerConfigured ? t('im.reader', { model: q.model ?? '' }) : t('im.noReader')}
          {' '}{t('im.privacy')}
        </p>
      )}

      <div className={`panel dropzone${dragging ? ' over' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); upload(Array.from(e.dataTransfer.files)); }}>
        <div>
          <b>{t('im.drop')}</b>
          <span className="sub">{t('im.dropHint')}</span>
        </div>
        <label className={`btn primary${uploading ? ' busy' : ''}`}>
          {uploading ? t('im.uploading', { done: uploading.done, total: uploading.total }) : t('im.choose')}
          <input ref={input} type="file" multiple hidden accept="application/pdf,image/jpeg,image/png,image/webp"
            disabled={!!uploading}
            onChange={(e) => { const files = Array.from(e.currentTarget.files ?? []); e.currentTarget.value = ''; upload(files); }} />
        </label>
      </div>
      {errors.length > 0 && <div className="note bad" role="alert">{errors.map((m) => <div key={m}>{m}</div>)}</div>}
      {duplicates.length > 0 && <p className="note">{t('im.duplicates', { list: duplicates.join(', ') })}</p>}

      <div className="tabs" role="tablist">
        {TABS.map((k) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>
            {t(`im.tab.${k}`)} {k !== 'all' && count(k) > 0 && <span className={k === 'review' || k === 'failed' ? 'count' : 'count quiet'}>{count(k)}</span>}
          </button>
        ))}
      </div>

      {list.error ? <LoadError message={list.error} onRetry={list.reload} /> : (
        <div className="panel table-wrap" aria-busy={list.loading}>
          {docs.length === 0 ? <p className="empty">{t(`im.empty.${tab}`)}</p> : (
            <table>
              <thead>
                <tr>
                  <th>{t('im.col.document')}</th><th>{t('im.col.type')}</th><th>{t('im.col.party')}</th>
                  <th className="num">{t('im.col.total')}</th><th>{t('im.col.status')}</th><th>{t('im.col.uploaded')}</th>
                </tr>
              </thead>
              <tbody>
                {docs.map((d: ImportDoc) => (
                  <tr key={d.id} className="row" onClick={() => navigate(`/import/${d.id}`)}>
                    <td className="name-cell">
                      <Link to={`/import/${d.id}`} onClick={(e) => e.stopPropagation()}>{d.documentNumber ?? d.fileName}</Link>
                      <span className="sub">{d.documentNumber ? d.fileName : d.summary ?? ''}</span>
                    </td>
                    <td>{d.docType ? t(`im.type.${d.docType}`) : '—'}</td>
                    <td>{d.partyName ?? '—'}</td>
                    <td className="num">{d.totalAmount && d.currency ? money(Number(d.totalAmount), d.currency) : '—'}</td>
                    <td><ImportStatusTag status={d.status} />
                      {d.status === 'FAILED' && d.error && <span className="sub neg">{d.error}</span>}</td>
                    <td>{date(d.uploadedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </>
  );
}
