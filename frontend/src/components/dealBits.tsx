import type { DealStatus, DealWarning, RfqStatus } from '../api';
import { useI18n } from '../i18n';

export function DealStatusTag({ status }: { status: DealStatus }) {
  const { t } = useI18n();
  return <span className={`pill d-${status}`}>{t(`deal.status.${status}`)}</span>;
}

export function RfqStatusTag({ status }: { status: RfqStatus }) {
  const { t } = useI18n();
  return <span className={`pill r-${status}`}>{t(`rfq.status.${status}`)}</span>;
}

export function Warnings({ warnings }: { warnings: DealWarning[] }) {
  const { t } = useI18n();
  if (!warnings.length) return null;
  return (
    <ul className="warnings" role="status">
      {warnings.map((w) => <li key={w} className={w.endsWith('_RED') ? 'bad' : ''}>{t(`warn.${w}`)}</li>)}
    </ul>
  );
}
