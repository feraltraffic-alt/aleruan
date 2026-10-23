import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { localToday } from '../dates';
import { I18nProvider } from '../i18n';
import { invoiceAmounts } from '../invoiceMath';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);

describe('invoice maths mirrors the backend', () => {
  it('rounds lines, then VAT on the subtotal', () => {
    expect(invoiceAmounts([{ quantity: 3, unitPrice: 0.335 }, { quantity: 1, unitPrice: 100 }], 'STANDARD', 21))
      .toEqual({ subtotal: 101.01, vatAmount: 21.21, total: 122.22 });
    expect(invoiceAmounts([{ quantity: 2, unitPrice: 50 }], 'REVERSE_CHARGE_EU', 21)).toEqual({ subtotal: 100, vatAmount: 0, total: 100 });
  });
});

describe('invoices in the demo API', () => {
  it('drafts from deal lines, numbers without gaps, takes payments and credits', async () => {
    const a = createMockApi();
    const [gliwice] = (await a.listDeals()).filter((d) => d.title.startsWith('Hala'));
    const before = await a.listInvoices();
    const draft = await a.draftInvoice(gliwice.id, 'DEAL_LINES');
    expect(draft).toMatchObject({ status: 'DRAFT', number: null, currency: 'EUR' });
    expect(draft.lines.length).toBeGreaterThan(0);
    await expect(a.payInvoice(draft.id, 10, localToday())).rejects.toThrow(/issued, unpaid/);
    const edited = await a.editInvoice(draft.id, { language: 'CS', vatMode: 'STANDARD', vatRate: 21,
      taxableDate: draft.taxableDate, dueDate: draft.dueDate, note: null,
      lines: [{ description: 'Montáž', unit: 't', quantity: 2, unitPrice: 1000, stageId: null }] });
    expect(edited).toMatchObject({ subtotal: 2000, vatAmount: 420, total: 2420 });
    const issued = await a.issueInvoice(draft.id);
    const year = localToday().slice(0, 4);
    const seq = before.filter((i) => i.kind === 'INVOICE' && i.number).length + 1;
    expect(issued.number).toBe(`FV-${year}-${String(seq).padStart(4, '0')}`);
    expect(issued.exchangeRate).toBeGreaterThan(20);
    expect(issued.vatAmountCzk).toBeCloseTo(420 * issued.exchangeRate!, 2);
    await expect(a.editInvoice(issued.id, { ...edited, lines: edited.lines })).rejects.toThrow(/credit note/);
    await expect(a.deleteInvoice(issued.id)).rejects.toThrow(/never deleted/);
    expect((await a.payInvoice(issued.id, 1000, localToday())).status).toBe('ISSUED');
    expect((await a.payInvoice(issued.id, 1420, localToday())).status).toBe('PAID');
    const note = await a.creditInvoice(issued.id, 'Chybná sazba');
    expect(note).toMatchObject({ kind: 'CREDIT_NOTE', total: -2420, creditOfId: issued.id });
    expect(note.number).toMatch(new RegExp(`^OD-${year}-\\d{4}$`));
    expect((await a.getInvoice(issued.id)).status).toBe('CREDITED');
  });
});

describe('invoice screens', () => {
  it('lists invoices with what customers owe and the overdue one', async () => {
    renderAt('/invoices');
    expect(await screen.findByText('Просрочена')).toBeInTheDocument();
    expect(screen.getByText('Клиенты должны, EUR')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('Только просроченные'));
    await waitFor(() => expect(within(screen.getByRole('table')).queryByText('Оплачена')).toBeNull());
    expect(within(screen.getByRole('table')).getByText('Просрочена')).toBeInTheDocument();
  });

  it('invoices deal lines from the deal page, edits the draft and issues it', async () => {
    const tab = { location: { href: '' } };
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
    URL.createObjectURL = vi.fn(() => 'blob:pdf');
    URL.revokeObjectURL = vi.fn();
    renderAt('/deals');
    fireEvent.click(await screen.findByText('Fasáda, Wesel'));
    const block = await screen.findByRole('region', { name: 'Фактуры по сделке' });
    expect(await within(block).findByText('Договор')).toBeInTheDocument();
    fireEvent.click(within(block).getByRole('button', { name: 'Фактура по позициям' }));
    const first = await screen.findByLabelText('Описание строки 1');
    fireEvent.change(first, { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Выставить фактуру' }));
    expect(await screen.findByText('Строка 1: нужно описание.')).toBeInTheDocument();
    fireEvent.change(first, { target: { value: 'Zateplení fasády, 1. etapa' } });
    fireEvent.change(screen.getByLabelText('Количество, строка 1'), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: 'Выставить фактуру' }));
    expect(await screen.findByRole('heading', { name: /^FV-\d{4}-\d{4}$/ })).toBeInTheDocument();
    expect(screen.getByText('Ждёт оплаты')).toBeInTheDocument();
    expect(screen.getByText(/CZK за 1 EUR/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Открыть PDF' }));
    await waitFor(() => expect(tab.location.href).toBe('blob:pdf'));
  });

  it('shows the Money S4 placeholder', async () => {
    renderAt('/invoices');
    fireEvent.click(await screen.findByRole('link', { name: 'Экспорт в Money S4' }));
    expect(await screen.findByText('В разработке')).toBeInTheDocument();
    expect(screen.getByText(/образец файла импорта/)).toBeInTheDocument();
  });
});
