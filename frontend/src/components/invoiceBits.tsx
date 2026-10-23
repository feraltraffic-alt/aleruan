import type { InvoiceStatus } from '../api';
import { useI18n } from '../i18n';

export function InvoiceStatusTag({ status, overdue }: { status: InvoiceStatus; overdue?: boolean }) {
  const { t } = useI18n();
  if (overdue) return <span className="pill inv-OVERDUE">{t('inv.overdueTag')}</span>;
  return <span className={`pill inv-${status}`}>{t(`inv.status.${status}`)}</span>;
}
