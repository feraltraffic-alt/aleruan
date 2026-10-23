import type { SiteStatus } from '../api';
import { useI18n } from '../i18n';

export function SiteStatusTag({ status }: { status: SiteStatus }) {
  const { t } = useI18n();
  return <span className={`pill s-${status}`}>{t(`site.status.${status}`)}</span>;
}

export function Progress({ pct, late }: { pct: number; late?: boolean }) {
  return (
    <span className={`progress${late ? ' late' : ''}`} role="img" aria-label={`${pct} %`}>
      <span style={{ width: `${pct}%` }} />
    </span>
  );
}
