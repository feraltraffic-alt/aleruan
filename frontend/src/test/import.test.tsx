import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { api } from '../api';
import { createMockApi } from '../api/mock';
import type { ImportDetail, ImportReview } from '../api';
import { I18nProvider } from '../i18n';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);
const pdf = (name: string) => new File(['%PDF-1.4\n%%EOF'], name, { type: 'application/pdf' });

const reviewOf = (d: ImportDetail, dealId: string | null): ImportReview => ({
  type: d.suggestion.type, documentNumber: d.read!.documentNumber, issueDate: d.read!.issueDate,
  taxableDate: d.read!.taxableDate, dueDate: d.read!.dueDate, currency: d.read!.currency,
  variableSymbol: d.read!.variableSymbol, netAmount: Number(d.read!.netAmount), vatAmount: Number(d.read!.vatAmount),
  totalAmount: Number(d.read!.totalAmount), vatNote: d.read!.vatNote,
  party: { ...d.suggestion.party, companyId: d.suggestion.party.companyId, address: d.suggestion.party.address },
  counterpartyId: d.suggestion.match?.id ?? null, role: d.suggestion.role, dealId,
});

describe('document import in the demo API', () => {
  it('reads an upload, registers the received invoice once and refuses it twice', { timeout: 15000 }, async () => {
    const a = createMockApi();
    const up = await a.uploadImport(pdf('faktura-elektro.pdf'), 'faktura-elektro.pdf');
    expect(up.duplicate).toBe(false);
    expect((await a.uploadImport(pdf('faktura-elektro.pdf'), 'faktura-elektro.pdf')).duplicate).toBe(true);
    await waitFor(async () => expect((await a.getImport(up.document.id)).document.status).toBe('EXTRACTED'), { timeout: 6000 });

    const d = await a.getImport(up.document.id);
    expect(d.suggestion.role).toBe('SUBCONTRACTOR');
    expect(d.suggestion.match?.legalName).toBe('Elektro Brno a.s.');
    expect(d.warnings.some((w) => w.code === 'READER_NOTE')).toBe(true);

    const [hala] = (await a.listDeals()).filter((x) => x.title.startsWith('Hala'));
    const out = await a.confirmImport(up.document.id, reviewOf(d, hala.id));
    expect(out.resultType).toBe('SUPPLIER_INVOICE');
    expect(out.counterpartyCreated).toBe(false);
    const received = await a.listSupplierInvoices({ dealId: hala.id });
    expect(received.some((i) => i.number === d.read!.documentNumber && i.supplier.legalName === 'Elektro Brno a.s.')).toBe(true);
    await expect(a.confirmImport(up.document.id, reviewOf(d, hala.id))).rejects.toThrow(/CONFIRMED/);

    const contract = await a.uploadImport(pdf('smlouva-kolin.pdf'), 'smlouva-kolin.pdf');
    await waitFor(async () => expect((await a.getImport(contract.document.id)).document.status).toBe('EXTRACTED'), { timeout: 6000 });
    const c = await a.getImport(contract.document.id);
    expect(c.suggestion.party.name).toBe('Investor Kolín a.s.');
    const co = await a.confirmImport(contract.document.id, { ...reviewOf(c, hala.id), netAmount: null, vatAmount: null, totalAmount: null });
    expect(co.resultType).toBe('DEAL_DOCUMENT');
    expect(co.counterpartyCreated).toBe(true);
    expect((await a.dealDocuments(hala.id)).length).toBe(1);
  });
});

describe('import screens', () => {
  it('shows the read document for review and confirms it', { timeout: 20000 }, async () => {
    renderAt('/import');
    expect(await screen.findByRole('heading', { name: 'Импорт документов' })).toBeInTheDocument();
    const link = await screen.findByRole('link', { name: /2026-0118|faktura-elektro-brno/ }, { timeout: 8000 });
    await waitFor(async () => expect((await api.listImports('EXTRACTED')).length).toBeGreaterThan(0), { timeout: 8000 });
    const [doc] = await api.listImports('EXTRACTED');
    expect(link).toBeInTheDocument();

    renderAt(`/import/${doc.id}`);
    const form = await screen.findByRole('button', { name: 'Подтвердить' }, { timeout: 5000 });
    expect(screen.getByText(/Claude: The stamp/)).toBeInTheDocument();
    expect(screen.getByText('Elektro Brno a.s.', { selector: 'a' })).toBeInTheDocument();
    fireEvent.click(form);
    await waitFor(() => {
      const alert = screen.queryByRole('alert');
      if (alert) throw new Error(`alert: ${alert.textContent}`);
      expect(screen.getByText(/Полученная фактура зарегистрирована/)).toBeInTheDocument();
    }, { timeout: 4000 });
  });

  it('lists received invoices on their own tab', async () => {
    renderAt('/invoices?tab=received');
    const table = await screen.findByRole('table');
    expect(await within(table).findByText('FV2026/0311')).toBeInTheDocument();
    expect(within(table).getByText('Stavby Horák s.r.o.')).toBeInTheDocument();
    const before = within(table).getAllByRole('button', { name: 'Отметить оплату' }).length;
    fireEvent.click(within(table).getAllByRole('button', { name: 'Отметить оплату' })[0]);
    fireEvent.click(within(table).getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(within(table).queryAllByRole('button', { name: 'Отметить оплату' }).length).toBe(before - 1));
  });
});

describe('review form parsing mirrors the server', () => {
  it('reads local amount and date formats', async () => {
    const { amount, isoDay } = await import('../importParse');
    expect(amount('12 345,50')).toBe(12345.5);
    expect(amount('12.345,50 Kč')).toBe(12345.5);
    expect(amount('1,234.50')).toBe(1234.5);
    expect(amount('-1210')).toBe(-1210);
    expect(amount('')).toBeNull();
    expect(amount('n/a')).toBeNaN();
    expect(isoDay('15.3.2026')).toBe('2026-03-15');
    expect(isoDay('2026-03-15')).toBe('2026-03-15');
    expect(isoDay('March')).toBe('');
  });
});
