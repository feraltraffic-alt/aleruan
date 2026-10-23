import type { Site, SiteStatus } from '../api';
import type { MapPin } from './GoogleMap';

export const SITE_STATUSES: SiteStatus[] = ['PLANNED', 'ACTIVE', 'ON_HOLD', 'DONE'];

/** Late tasks are red, a paused site amber, finished grey, everything else green. */
export function siteTone(s: Pick<Site, 'status' | 'lateTasks'>): MapPin['tone'] {
  if (s.status === 'DONE') return 'idle';
  if (s.lateTasks > 0) return 'risk';
  if (s.status === 'ON_HOLD') return 'warn';
  return 'ok';
}
