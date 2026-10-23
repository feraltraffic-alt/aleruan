import { useEffect, useRef, useState } from 'react';
import { api, type FileRef, type PhotoOwner } from '../api';
import { useI18n } from '../i18n';
import { prepareUpload } from '../image';

/** Loads a stored file with the user's credentials and gives a temporary local URL for it. */
function useFileUrl(id: string) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    let made: string | null = null;
    api.fileBlob(id).then((b) => {
      if (!alive) return;
      made = URL.createObjectURL(b);
      setUrl(made);
    }).catch(() => { /* the tile shows the file name instead */ });
    return () => { alive = false; if (made) URL.revokeObjectURL(made); };
  }, [id]);
  return url;
}

function Tile({ file, onRemove }: { file: FileRef; onRemove?(): void }) {
  const { t } = useI18n();
  const url = useFileUrl(file.id);
  const image = file.contentType.startsWith('image/');
  return (
    <li className="photo">
      <a href={url ?? undefined} target="_blank" rel="noreferrer" aria-label={t('photo.open', { name: file.fileName })}>
        {image && url ? <img src={url} alt={file.fileName} loading="lazy" /> : <span className="photo-file">{image ? '…' : 'PDF'}</span>}
      </a>
      {onRemove && (
        <button className="photo-x" onClick={onRemove} aria-label={t('photo.remove', { name: file.fileName })}>×</button>
      )}
    </li>
  );
}

/** Photos of a diary entry or defect, with upload (several at once) and removal. */
export function Photos({ owner, ownerId, photos, editable, onChange }: {
  owner: PhotoOwner; ownerId: string; photos: FileRef[]; editable: boolean; onChange(next: FileRef[]): void;
}) {
  const { t } = useI18n();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = async (list: FileList | null) => {
    if (!list?.length) return;
    setBusy(true);
    setError(null);
    const added: FileRef[] = [];
    for (const f of Array.from(list)) {
      try {
        const { blob, name } = await prepareUpload(f);
        added.push(await api.uploadPhoto(owner, ownerId, blob, name));
      } catch (e) {
        setError(`${f.name}: ${e instanceof Error ? e.message : String(e)}`);
      }
    }
    onChange([...photos, ...added]);
    setBusy(false);
    if (input.current) input.current.value = '';
  };
  const remove = async (f: FileRef) => {
    try {
      await api.removePhoto(owner, ownerId, f.id);
      onChange(photos.filter((x) => x.id !== f.id));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  if (!photos.length && !editable) return null;
  return (
    <div className="photos">
      <ul>{photos.map((p) => <Tile key={p.id} file={p} onRemove={editable ? () => remove(p) : undefined} />)}</ul>
      {editable && (
        <label className={`btn small${busy ? ' busy' : ''}`}>
          {busy ? t('photo.uploading') : t('photo.add')}
          <input ref={input} type="file" accept="image/jpeg,image/png,image/webp,application/pdf" multiple hidden
            disabled={busy} onChange={(e) => upload(e.target.files)} />
        </label>
      )}
      {error && <span className="err">{error}</span>}
    </div>
  );
}
