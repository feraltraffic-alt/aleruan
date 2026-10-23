import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { I18nProvider } from '../i18n';
import { languageFor, suggestVat, vatAmount } from '../vat';

describe('VAT suggestion mirrors the backend', () => {
  it('covers the same cases as VatAdvisorTest', () => {
    expect(suggestVat('MD', null, null, false).mode).toBe('OUTSIDE_SCOPE');
    expect(suggestVat('DE', 'DE811234567', null, false)).toMatchObject({ mode: 'REVERSE_CHARGE_EU', reason: 'EU_BUSINESS_CUSTOMER' });
    expect(suggestVat('DE', null, null, false).reason).toBe('CHECK_WITH_ACCOUNTANT');
    expect(suggestVat('PL', 'PL2815901396', 'pl', true).reason).toBe('SITE_IN_OTHER_EU_STATE');
    expect(suggestVat('CZ', 'CZ68599528', 'DE', true).mode).toBe('REVERSE_CHARGE_EU');
    expect(suggestVat('CZ', null, 'DE', true).reason).toBe('CHECK_WITH_ACCOUNTANT');
    expect(suggestVat('CZ', 'CZ68599528', null, true).mode).toBe('REVERSE_CHARGE_CZ');
    expect(suggestVat('CZ', 'CZ68599528', null, false)).toEqual({ mode: 'STANDARD', rate: 21, reason: 'DOMESTIC_STANDARD' });
    expect(vatAmount(20604.7, 'STANDARD', 21)).toBe(4326.99);
    expect(vatAmount(20604.7, 'REVERSE_CHARGE_CZ', 21)).toBe(0);
    expect(languageFor('sk')).toBe('CS');
    expect(languageFor('BE')).toBe('EN');
  });
});

describe('quotes in the demo API', () => {
  it('issues versions, quotes a draft and refuses after acceptance', async () => {
    const a = createMockApi();
    const customer = (await a.listCounterparties(undefined, 'CUSTOMER')).find((c) => c.country === 'PL')!;
    let d = await a.createDeal({ title: 'Byt', customerId: customer.id, direction: 'CONSTRUCTION', siteCountry: 'PL',
      currency: 'EUR', defaultMarkupPct: 25 });
    await expect(a.issueQuote(d.id, { language: 'PL', validDays: 30, vatMode: 'STANDARD' })).rejects.toThrow(/no lines/);
    d = await a.addLine(d.id, { kind: 'WORK', description: 'Malowanie', quantity: 100, unitCost: 8 });
    expect((await a.quoteDefaults(d.id))).toMatchObject({ language: 'PL', vatMode: 'REVERSE_CHARGE_EU' });
    const q1 = await a.issueQuote(d.id, { language: 'PL', validDays: 14, vatMode: 'REVERSE_CHARGE_EU' });
    expect(q1.total).toBe(1000);
    expect((await a.getDeal(d.id)).status).toBe('QUOTED');
    const q2 = await a.issueQuote(d.id, { language: 'CS', validDays: 30, vatMode: 'STANDARD', vatRate: 21 });
    expect(q2.number).toBe(`${q1.number}/2`);
    expect(q2.total).toBe(1210);
    expect((await a.listQuotes(d.id)).map((q) => q.version)).toEqual([2, 1]);
    await a.moveDeal(d.id, 'WON');
    await expect(a.issueQuote(d.id, { language: 'CS', validDays: 30, vatMode: 'STANDARD' })).rejects.toThrow(/before the customer accepts/);
  });
});

describe('quote screen', () => {
  afterEach(() => vi.restoreAllMocks());

  it('suggests the VAT treatment, previews totals and lists the issued quote', async () => {
    const tab = { location: { href: '' } };
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
    URL.createObjectURL = vi.fn(() => 'blob:quote');
    URL.revokeObjectURL = vi.fn();

    render(<I18nProvider><MemoryRouter initialEntries={['/deals']}><AppRoutes /></MemoryRouter></I18nProvider>);
    fireEvent.click(await screen.findByText('Rekonstrukce kanceláří, Kraków'));
    fireEvent.click(await screen.findByRole('button', { name: 'Сформировать КП' }));
    const dialog = screen.getByRole('dialog', { name: 'Сформировать КП' });
    await within(dialog).findByText('рекомендуем');
    const suggested = within(dialog).getByLabelText(/Reverse charge ЕС/);
    expect(suggested).toBeChecked();
    expect(within(dialog).getByText(/объект в другой стране ЕС/)).toBeInTheDocument();
    expect((within(dialog).getByLabelText('Язык документа') as HTMLSelectElement).value).toBe('PL');

    fireEvent.click(within(dialog).getByLabelText(/Чешский НДС/));
    expect(within(dialog).getByText(/24\s?931,69/)).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Действует, дней'), { target: { value: '0' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сформировать PDF' }));
    expect(within(dialog).getByText('Целое число от 1 до 365.')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Действует, дней'), { target: { value: '14' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сформировать PDF' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(await screen.findByText(/^Q-\d{4}-\d{4}$/)).toBeInTheDocument();
    await waitFor(() => expect(tab.location.href).toBe('blob:quote'));
    expect(screen.getByText('Чешский НДС 21 %')).toBeInTheDocument();
  });
});
