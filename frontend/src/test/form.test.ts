import { describe, expect, it } from 'vitest';
import { validate } from '../pages/validateCounterparty';

const ok = { legalName: 'Stavby Horák s.r.o.', country: 'CZ', companyId: '68599528', vatNumber: 'CZ68599528', roles: ['SUBCONTRACTOR' as const] };

describe('counterparty form validation', () => {
  it('accepts a valid Czech company', () => {
    expect(validate(ok)).toEqual({});
  });
  it('reports each problem with its own message key', () => {
    expect(validate({ ...ok, legalName: ' ', companyId: '68599529', vatNumber: '123', roles: [], country: 'C' }))
      .toEqual({
        legalName: 'form.err.name', vatNumber: 'form.err.vat', roles: 'form.err.roles', country: 'form.err.country',
      });
    expect(validate({ ...ok, companyId: '68599529' }).companyId).toBe('form.err.ico');
  });
});
