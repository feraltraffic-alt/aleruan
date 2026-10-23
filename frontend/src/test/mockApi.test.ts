import { describe, expect, it } from 'vitest';
import { createMockApi } from '../api/mock';

describe('mock API mirrors backend rules', () => {
  it('creates, rejects duplicates, checks and audits', async () => {
    const api = createMockApi();
    const c = await api.createCounterparty({ legalName: 'Test s.r.o.', country: 'cz', companyId: '215 901 09', roles: ['SUPPLIER'] });
    expect(c.companyId).toBe('21590109');
    await expect(api.createCounterparty({ legalName: 'Dup', country: 'CZ', companyId: '21590109', roles: ['CUSTOMER'] }))
      .rejects.toThrow(/already exists/);
    const run = await api.runChecks(c.id);
    expect(run.status).toBe('GREEN');
    expect((await api.checkHistory(c.id)).length).toBe(1);
    expect((await api.auditHistory('Counterparty', c.id)).map((a) => a.action)).toEqual(['CREATED', 'CHECKED']);
  });
  it('asks for manual review when no registry covers the country', async () => {
    const api = createMockApi();
    const c = await api.createCounterparty({ legalName: 'Moldova SRL', country: 'MD', companyId: '1', roles: ['SUBCONTRACTOR'] });
    const run = await api.runChecks(c.id);
    expect(run.findings).toEqual(['MANUAL_REVIEW_REQUIRED']);
    expect(run.status).toBe('YELLOW');
  });
  it('filters by role and text', async () => {
    const api = createMockApi();
    expect((await api.listCounterparties(undefined, 'CARRIER')).map((c) => c.legalName)).toEqual(['Transkom Logistyka Sp. z o.o.']);
    expect((await api.listCounterparties('68599528')).length).toBe(1);
  });
});
