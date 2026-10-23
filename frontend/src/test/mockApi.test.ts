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
    expect((await api.checkHistory(c.id)).map((r) => r.source)).toEqual(['ARES', 'REGISTR_DPH']);
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

  it('checks bank accounts: published, not published, manual', async () => {
    const api = createMockApi();
    const [cz] = await api.listCounterparties('68599528');
    expect((await api.checkAccount(cz.id, '19-2000145399/0800')).findings).toEqual([]);
    expect((await api.checkAccount(cz.id, '999-1/0100')).findings).toEqual(['ACCOUNT_NOT_PUBLISHED']);
    const [md] = await api.listCounterparties('Softline');
    expect((await api.checkAccount(md.id, 'MD24AG000225100013104168')).findings).toEqual(['MANUAL_REVIEW_REQUIRED']);
    expect((await api.checkHistory(cz.id)).map((r) => r.checkedAccount)).toEqual(['999-1/0100', '19-2000145399/0800']);
  });
});
