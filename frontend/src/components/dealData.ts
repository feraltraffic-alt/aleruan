import { api, type DealStatus, type WorkType } from '../api';
import { useI18n, type Lang } from '../i18n';
import { useLoad } from './useLoad';

export const DEAL_STATUSES: DealStatus[] = ['DRAFT', 'QUOTED', 'WON', 'IN_PROGRESS', 'COMPLETED', 'LOST', 'CANCELLED'];
export const DIRECTIONS = ['CONSTRUCTION', 'MARKETING', 'IT', 'CONSULTING', 'LOGISTICS', 'TRADE'] as const;
export const LINE_KINDS = ['WORK', 'MATERIAL', 'SERVICE', 'GOODS', 'DELIVERY'] as const;
export const CURRENCIES = ['EUR', 'CZK', 'PLN'];

const NAME: Record<Lang, (w: WorkType) => string> = { ru: (w) => w.nameRu, cs: (w) => w.nameCs, en: (w) => w.nameEn };

/** Work types with names in the current language. */
export function useWorkTypes() {
  const { lang } = useI18n();
  const { data } = useLoad(() => api.workTypes(), []);
  const list = (data ?? []).filter((w) => w.active);
  const name = (code: string | null | undefined) => {
    if (!code) return '';
    const w = data?.find((x) => x.code === code);
    return w ? NAME[lang](w) : code;
  };
  return { list, name };
}
