import { api, type WorkType } from '../api';
import { LoadError } from '../components/LoadError';
import { useLoad } from '../components/useLoad';
import { useI18n, type Lang } from '../i18n';

const NAME: Record<Lang, (w: WorkType) => string> = {
  ru: (w) => w.nameRu, cs: (w) => w.nameCs, en: (w) => w.nameEn,
};

export function ReferencePage() {
  const { t, lang } = useI18n();
  const types = useLoad(() => api.workTypes(), []);
  return (
    <>
      <div className="page-head">
        <div>
          <h1>{t('ref.title')}</h1>
          <p className="lead">{t('ref.lead')}</p>
        </div>
      </div>
      {types.error ? <LoadError message={types.error} onRetry={types.reload} /> : (
        <div className="panel table-wrap">
          <table>
            <thead><tr><th>{t('ref.name')}</th><th>{t('ref.code')}</th><th>{t('ref.markup')}</th></tr></thead>
            <tbody>
              {types.data?.map((w) => (
                <tr key={w.id}>
                  <td>{NAME[lang](w)}<span className="sub">{w.namePl}</span></td>
                  <td className="mono-ish">{w.code}</td>
                  <td>{w.defaultMarkupPct != null ? `${w.defaultMarkupPct} %` : <span className="sub">{t('ref.notSet')}</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
