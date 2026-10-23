import { FORBIDDEN } from '../api/http';
import { useI18n } from '../i18n';

export function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  const { t } = useI18n();
  if (message === FORBIDDEN) {
    return <div className="panel error-box" role="alert"><span>{t('err.forbidden')}</span></div>;
  }
  return (
    <div className="panel error-box" role="alert">
      <span>{t('err.load', { msg: message })}</span>
      <button className="btn" onClick={onRetry}>{t('err.retry')}</button>
    </div>
  );
}
