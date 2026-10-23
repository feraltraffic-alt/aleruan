import type { CheckStatus } from '../api';
import { useI18n } from '../i18n';

export function Status({ status }: { status: CheckStatus | null }) {
  const { t } = useI18n();
  return <span className={`status ${status ?? 'none'}`}>{status ? t(`status.${status}`) : t('status.none')}</span>;
}

export function Stripe({ status }: { status: CheckStatus | null }) {
  return <td className={`stripe s-${status ?? 'none'}`} aria-hidden="true" />;
}
