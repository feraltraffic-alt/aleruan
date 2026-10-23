import { useState, type FormEvent } from 'react';
import { can } from '../permissions';
import { api, type DiaryData, type DiaryEntry, type Site } from '../api';
import { LoadError } from '../components/LoadError';
import { Photos } from '../components/Photos';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { localToday } from '../dates';
import { useI18n } from '../i18n';

type Mode = { kind: 'new' } | { kind: 'edit'; entry: DiaryEntry } | { kind: 'correct'; entry: DiaryEntry };

/** Site diary: newest day first; entries lock after a day and are corrected by new entries. */
export function DiarySection({ site }: { site: Site }) {
  const { t, day, date } = useI18n();
  const entries = useLoad(() => api.diary(site.id), [site.id]);
  const [mode, setMode] = useState<Mode | null>(null);

  const replace = (e: DiaryEntry) => entries.setData((list) => {
    const rest = (list ?? []).filter((x) => x.id !== e.id);
    return [e, ...rest].sort((a, b) => b.entryDate.localeCompare(a.entryDate) || b.createdAt.localeCompare(a.createdAt));
  });
  const byDay = new Map<string, DiaryEntry[]>();
  entries.data?.forEach((e) => byDay.set(e.entryDate, [...(byDay.get(e.entryDate) ?? []), e]));
  const find = (id: string) => entries.data?.find((x) => x.id === id);

  return (
    <section className="section" aria-label={t('diary.title')}>
      <div className="section-head">
        <div>
          <h2>{t('diary.title')}</h2>
          <p className="lead">{t('diary.lead')}</p>
        </div>
        {!mode && can('site.records') && <button className="btn primary" onClick={() => setMode({ kind: 'new' })}>{t('diary.add')}</button>}
      </div>
      {mode && (
        <DiaryForm site={site} mode={mode} onCancel={() => setMode(null)}
          onSaved={() => { setMode(null); void entries.reload(); }} />
      )}
      {entries.error ? <LoadError message={entries.error} onRetry={entries.reload} /> : (
        entries.data?.length === 0 ? <div className="panel"><p className="empty">{t('diary.empty')}</p></div> : (
          <ol className="diary">
            {[...byDay.entries()].map(([d, list]) => (
              <li key={d} className="diary-day">
                <h3>{day(d)}</h3>
                {list.map((e) => (
                  <article key={e.id} className={`panel diary-entry${e.correctedBy.length ? ' corrected' : ''}`}>
                    <header>
                      <span className="sub">
                        {e.author ?? '—'}, {date(e.createdAt)}
                        {e.editedAt && ` · ${t('diary.edited')}`}
                      </span>
                      <span className="diary-meta">
                        {e.weather && <span className="tag">{e.weather}</span>}
                        {e.workers != null && <span className="tag">{t('diary.workersN', { n: e.workers })}</span>}
                      </span>
                    </header>
                    {e.correctsId && (
                      <p className="sub correction">{t('diary.corrects', { when: find(e.correctsId) ? date(find(e.correctsId)!.createdAt) : '' })}</p>
                    )}
                    <p className="diary-text">{e.text}</p>
                    {e.correctedBy.length > 0 && <p className="sub neg">{t('diary.wasCorrected')}</p>}
                    <Photos owner="diary" ownerId={e.id} photos={e.photos} editable={e.editable && can('site.records')}
                      onChange={(photos) => replace({ ...e, photos })} />
                    <div className="row-actions">
                      {!can('site.records') ? null : e.editable
                        ? <button className="linkish" onClick={() => setMode({ kind: 'edit', entry: e })}>{t('line.editShort')}</button>
                        : <button className="linkish" onClick={() => setMode({ kind: 'correct', entry: e })}>{t('diary.correct')}</button>}
                    </div>
                  </article>
                ))}
              </li>
            ))}
          </ol>
        )
      )}
    </section>
  );
}

function DiaryForm({ site, mode, onCancel, onSaved }: {
  site: Site; mode: Mode; onCancel(): void; onSaved(e: DiaryEntry): void;
}) {
  const { t, date } = useI18n();
  const action = useAction();
  const base = mode.kind === 'new' ? null : mode.entry;
  const [today] = useState(localToday);
  const [f, setF] = useState({
    entryDate: base?.entryDate ?? today,
    weather: mode.kind === 'edit' ? base?.weather ?? '' : '',
    workers: mode.kind === 'edit' && base?.workers != null ? String(base.workers) : '',
    text: mode.kind === 'edit' ? base?.text ?? '' : '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = (k: keyof typeof f, v: string) => { setF((x) => ({ ...x, [k]: v })); setErrors((e) => ({ ...e, [k]: '' })); };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const workers = f.workers.trim() === '' ? null : Number(f.workers);
    const errs: Record<string, string> = {};
    if (!f.entryDate || f.entryDate > today) errs.entryDate = t('diary.err.date');
    if (workers != null && !(Number.isInteger(workers) && workers >= 0 && workers <= 1000)) errs.workers = t('diary.err.workers');
    if (!f.text.trim()) errs.text = t('diary.err.text');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const data: DiaryData = { entryDate: f.entryDate, weather: f.weather.trim() || null, workers, text: f.text.trim(),
      correctsId: mode.kind === 'correct' ? mode.entry.id : null };
    const saved = await action.run(() => (mode.kind === 'edit' ? api.editDiaryEntry(mode.entry.id, data)
      : api.addDiaryEntry(site.id, data)));
    if (saved) onSaved(saved);
  };

  return (
    <form className="panel box diary-form" onSubmit={submit} noValidate
      aria-label={mode.kind === 'correct' ? t('diary.correct') : mode.kind === 'edit' ? t('line.editShort') : t('diary.add')}>
      {mode.kind === 'correct' && (
        <p className="note">{t('diary.correctHint', { when: date(mode.entry.createdAt) })}<br /><q>{mode.entry.text}</q></p>
      )}
      <div className="grid3">
        <div className="field">
          <label htmlFor="d-date">{t('diary.date')}</label>
          <input id="d-date" type="date" className="input" value={f.entryDate} max={today} aria-invalid={!!errors.entryDate}
            onChange={(e) => set('entryDate', e.target.value)} />
          {errors.entryDate && <span className="err">{errors.entryDate}</span>}
        </div>
        <div className="field">
          <label htmlFor="d-workers">{t('diary.workers')}</label>
          <input id="d-workers" className="input" inputMode="numeric" value={f.workers} aria-invalid={!!errors.workers}
            onChange={(e) => set('workers', e.target.value)} />
          {errors.workers && <span className="err">{errors.workers}</span>}
        </div>
        <div className="field">
          <label htmlFor="d-weather">{t('diary.weather')}</label>
          <input id="d-weather" className="input" value={f.weather} placeholder={t('diary.weatherHint')}
            onChange={(e) => set('weather', e.target.value)} />
        </div>
      </div>
      <div className="field">
        <label htmlFor="d-text">{t('diary.text')}</label>
        <textarea id="d-text" className="input" rows={5} value={f.text} aria-invalid={!!errors.text}
          placeholder={t('diary.textHint')} onChange={(e) => set('text', e.target.value)} />
        {errors.text && <span className="err">{errors.text}</span>}
      </div>
      {mode.kind === 'new' && <p className="sub">{t('diary.photosAfter')}</p>}
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      <div className="actions">
        <button className="btn primary" disabled={action.busy}>{t('common.save')}</button>
        <button type="button" className="btn" onClick={onCancel}>{t('form.cancel')}</button>
      </div>
    </form>
  );
}
