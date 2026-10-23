import type { EvidenceCheck } from '../api';
import { useI18n, type MessageKey } from '../i18n';

const MONEY = new Set(['accepted', 'contract']);
const DAYS = new Set(['date', 'due']);

/** Text of an evidence completeness check in the current language, with amounts, dates and statuses formatted. */
export function useCheckText() {
  const { t, day, money } = useI18n();
  const tr = (key: string, vars?: Record<string, string | number>) => {
    const text = t(key as MessageKey, vars);
    return text === key ? null : text;
  };
  return (c: EvidenceCheck) => {
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
}
