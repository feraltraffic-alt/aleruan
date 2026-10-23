import type { NewCounterparty } from '../api';
import { companyIdError, isPlausibleVat } from '../identifiers';
import type { MessageKey } from '../i18n';

export type Errors = Partial<Record<'country' | 'companyId' | 'legalName' | 'vatNumber' | 'roles', MessageKey>>;

export function validate(f: NewCounterparty): Errors {
  const e: Errors = {};
  if (!/^[A-Za-z]{2}$/.test(f.country)) e.country = 'form.err.country';
  const idErr = companyIdError(f.country.toUpperCase(), f.companyId ?? '');
  if (idErr) e.companyId = idErr;
  if (!f.legalName.trim()) e.legalName = 'form.err.name';
  if (f.vatNumber && !isPlausibleVat(f.vatNumber)) e.vatNumber = 'form.err.vat';
  if (f.roles.length === 0) e.roles = 'form.err.roles';
  return e;
}
