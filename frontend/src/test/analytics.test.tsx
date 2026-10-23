import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { marginCuts, NONE } from '../api/mockAnalytics';
import { I18nProvider } from '../i18n';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);

describe('margin cuts mirror the backend', () => {
  it('counts a deal once per group and puts lines without a subcontractor together', () => {
    const r = marginCuts([
      { id: 'd1', number: 'D-1', title: 'Hala', customerId: 'c1', customerName: 'Alfa', direction: 'CONSTRUCTION',
        country: 'PL', month: '2026-09', lines: [
          { workType: 'INSTALLATION', subId: 's1', subName: 'S1', cost: 1000, price: 1300 },
          { workType: 'INSTALLATION', subId: 's1', subName: 'S1', cost: 500, price: 600 },
          { workType: null, subId: null, subName: null, cost: 100, price: 100 }] },
      { id: 'd2', number: 'D-2', title: 'Sklad', customerId: 'c1', customerName: 'Alfa', direction: 'CONSTRUCTION',
        country: null, month: '2026-08', lines: [{ workType: null, subId: null, subName: null, cost: 0, price: 0 }] },
    ]);
    expect(r.totals).toEqual({ deals: 2, price: 2000, cost: 1600, margin: 400, marginPct: 20 });
    expect(r.cuts.SUBCONTRACTOR[0]).toMatchObject({ key: 's1', deals: 1, margin: 400 });
    expect(r.cuts.SUBCONTRACTOR.find((x) => x.key === NONE)?.deals).toBe(2);
    expect(r.cuts.MONTH.map((m) => m.key)).toEqual(['2026-08', '2026-09']);
    expect(r.cuts.DEAL.find((x) => x.key === 'd2')?.marginPct).toBeNull();
  });
});

describe('analytics in the demo API', () => {
  it('converts currencies, splits agreed deals from open quotes and lists risks', async () => {
    const a = createMockApi();
    const czk = await a.analyticsMargin();
    expect(czk.totals.deals).toBe(2); // the two running demo deals
    const sum = czk.cuts.DEAL.reduce((s, r) => s + r.price, 0);
    expect(sum).toBeCloseTo(czk.totals.price, 2);
    const eur = await a.analyticsMargin({ currency: 'EUR' });
    expect(eur.totals.price * 25.2).toBeCloseTo(czk.totals.price, -1);
    expect((await a.analyticsMargin({ scope: 'PIPELINE' })).totals.deals).toBe(1);
    expect((await a.analyticsMargin({ country: 'DE' })).totals.deals).toBe(1);
    await expect(a.analyticsMargin({ currency: 'XXX' })).rejects.toThrow(/rate/);

    const h = await a.analyticsHealth();
    expect(h.sites.length).toBe(2);
    expect(h.counterparties.map((p) => p.status)).toContain('RED');
    expect(h.counterparties[0].status).toBe('RED');
    expect(h.packages.length).toBeGreaterThan(0);
    expect(h.packages[0].gaps.some((g) => g.code === 'CONTRACT_ATTACHED')).toBe(true);
  });
});

describe('analytics page', () => {
  it('shows totals, switches cuts and lists risks', { timeout: 20000 }, async () => {
    renderAt('/analytics');
    expect(await screen.findByRole('heading', { name: 'Аналитика' })).toBeInTheDocument();
    expect(await screen.findByText('Договорная стоимость')).toBeInTheDocument();
    await screen.findByText('Hala magazynowa, Gliwice', { exact: false });
    const table = screen.getAllByRole('table')[0];
    expect(within(table).getByText('Hala magazynowa, Gliwice', { exact: false })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Исполнители' }));
    expect(await screen.findByText('Elektro Brno a.s.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: 'Открытые КП' }));
    await waitFor(() => expect(screen.queryByText('Elektro Brno a.s.')).toBeInTheDocument());
    expect(await screen.findByText('Неполные доказательные пакеты', {}, { timeout: 8000 })).toBeInTheDocument();
    const risks = screen.getByRole('region', { name: 'Риски' });
    expect(within(risks).getByText('Montage Rhein GmbH')).toBeInTheDocument();
  });
});
