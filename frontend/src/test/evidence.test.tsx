import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import { api } from '../api';
import { createMockApi } from '../api/mock';
import { safeName } from '../api/mockEvidence';
import { I18nProvider } from '../i18n';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);
const pdf = () => new File(['%PDF-1.4\n%%EOF'], 'Smlouva o dílo – podepsaná.pdf', { type: 'application/pdf' });

describe('evidence package in the demo API', () => {
  it('flags what is missing, takes contract papers and records the export', async () => {
    const a = createMockApi();
    const [hala] = (await a.listDeals()).filter((d) => d.title.startsWith('Hala'));
    const before = await a.evidence(hala.id);
    const level = (o: typeof before, code: string) => o.checks.find((c) => c.code === code)?.level;
    expect(level(before, 'CONTRACT_ATTACHED')).toBe('MISSING');
    expect(level(before, 'INVOICE_WITHOUT_ACCEPTANCE')).toBe('WARN'); // the seeded advance invoice has no stage
    expect(level(before, 'AUDIT_CHAIN')).toBe('OK');
    expect(before.checks[0].level).toBe('MISSING');
    expect(before.lastExport).toBeNull();

    const doc = await a.uploadDealDocument(hala.id, pdf(), 'Smlouva o dílo – podepsaná.pdf');
    const after = await a.evidence(hala.id);
    expect(level(after, 'CONTRACT_ATTACHED')).toBe('OK');
    expect(after.items.find((i) => i.section === 'CONTRACT')?.files[0].path)
      .toBe('01-contract/Smlouva_o_dilo_podepsana.pdf');
    expect(after.score.ok).toBe(before.score.ok + 1);

    const exported = await a.exportEvidence(hala.id);
    expect(exported.fileName).toMatch(/^evidence-D-\d{4}-\d{4}-\d{8}-demo\.txt$/);
    expect((await a.evidence(hala.id)).lastExport?.sha256sums).toBe(exported.sha256sums);
    await a.removeDealDocument(hala.id, doc.id);
    expect(level(await a.evidence(hala.id), 'CONTRACT_ATTACHED')).toBe('MISSING');
    await expect(a.uploadDealDocument(hala.id, new File(['x'], 'a.exe'), 'a.exe')).rejects.toThrow(/PDF/);
  });

  it('keeps archive names plain', () => {
    expect(safeName('../../etc/passwd')).toBe('etc_passwd');
    expect(safeName('Příloha č. 1.pdf')).toBe('Priloha_c._1.pdf');
    expect(safeName('///')).toBe('file');
  });
});

describe('evidence section on the deal page', () => {
  it('shows gaps first, attaches the contract from the checklist and downloads the package', async () => {
    const [hala] = (await api.listDeals()).filter((d) => d.title.startsWith('Hala'));
    renderAt(`/deals/${hala.id}`);
    const section = await screen.findByRole('region', { name: 'Доказательный пакет' });
    expect(await within(section).findByText('Не приложен подписанный договор или заказ')).toBeInTheDocument();
    expect(within(section).queryByText('Цепочка журнала аудита цела')).toBeNull();
    fireEvent.click(within(section).getByRole('button', { name: /Показать, что в порядке/ }));
    expect(within(section).getByText('Цепочка журнала аудита цела')).toBeInTheDocument();

    const input = section.querySelector('input[type=file]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [pdf()] } });
    expect(await within(section).findByRole('button', { name: 'Smlouva o dílo – podepsaná.pdf' })).toBeInTheDocument();
    await waitFor(() => expect(within(section).queryByText('Не приложен подписанный договор или заказ')).toBeNull());

    const click = vi.fn();
    URL.createObjectURL = vi.fn(() => 'blob:demo');
    URL.revokeObjectURL = vi.fn();
    HTMLAnchorElement.prototype.click = click;
    fireEvent.click(within(section).getByRole('button', { name: 'Скачать пакет (ZIP)' }));
    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(await within(section).findByText(/Последняя выгрузка/)).toBeInTheDocument();
  });
});
