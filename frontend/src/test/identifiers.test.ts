import { describe, expect, it } from 'vitest';
import { companyIdError, isPlausibleVat, isValidCzIco, isValidPlNip } from '../identifiers';

describe('identifiers (same cases as backend TaxIdentifiersTest)', () => {
  it('validates Czech IČO', () => {
    expect(isValidCzIco('14220997')).toBe(true);
    expect(isValidCzIco('142 20 997')).toBe(true);
    expect(isValidCzIco('14220998')).toBe(false);
  });
  it('validates Polish NIP', () => {
    expect(isValidPlNip('5223057790')).toBe(true);
    expect(isValidPlNip('897-180-69-51')).toBe(true);
    expect(isValidPlNip('5223057791')).toBe(false);
  });
  it('checks VAT shape', () => {
    expect(isPlausibleVat('CZ 14220997')).toBe(true);
    expect(isPlausibleVat('14220997')).toBe(false);
  });
  it('only enforces checksums for CZ and PL', () => {
    expect(companyIdError('CZ', '12345678')).toBe('form.err.ico');
    expect(companyIdError('PL', '1234567890')).toBe('form.err.nip');
    expect(companyIdError('MD', '1023600005748')).toBeNull();
    expect(companyIdError('CZ', '')).toBeNull();
  });
});
