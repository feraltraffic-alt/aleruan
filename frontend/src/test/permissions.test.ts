import { describe, expect, it } from 'vitest';
import { can } from '../permissions';

describe('permissions mirror the server rules', () => {
  it('lets each role do its job and nothing more', () => {
    expect(can('site.records', ['FOREMAN'])).toBe(true);
    expect(can('sites.read', ['FOREMAN'])).toBe(true);
    expect(can('deals.read', ['FOREMAN'])).toBe(false);
    expect(can('invoices.write', ['FOREMAN'])).toBe(false);
    expect(can('invoices.write', ['ACCOUNTANT'])).toBe(true);
    expect(can('deals.write', ['ACCOUNTANT'])).toBe(false);
    expect(can('quotes.issue', ['SALES_MANAGER'])).toBe(true);
    expect(can('quotes.issue', ['PROJECT_MANAGER'])).toBe(false);
    expect(can('tenders.write', ['PROJECT_MANAGER'])).toBe(true);
    expect(can('sites.read', ['WAREHOUSE_KEEPER'])).toBe(false);
    expect(can('audit.read', ['SALES_MANAGER'])).toBe(false);
    expect(can('reference.write', ['ADMIN'])).toBe(true);
    expect(can('deals.read', [])).toBe(false);
    expect(can('deals.read', ['FOREMAN', 'ACCOUNTANT'])).toBe(true);
  });

  it('allows everything without login (demo and local development)', () => {
    expect(can('invoices.write', null)).toBe(true);
    expect(can('invoices.write')).toBe(true); // tests run in demo mode
  });
});
