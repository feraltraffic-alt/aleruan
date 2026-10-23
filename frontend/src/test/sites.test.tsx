import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { addDays, daysInclusive, mondayOf } from '../dates';
import { I18nProvider } from '../i18n';
import { loadGoogleMaps } from '../maps';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);

describe('date helpers', () => {
  it('works in whole days without time-zone drift', () => {
    expect(mondayOf('2026-10-23')).toBe('2026-10-19');
    expect(mondayOf('2026-10-19')).toBe('2026-10-19');
    expect(mondayOf('2026-10-25')).toBe('2026-10-19');
    expect(addDays('2026-10-30', 3)).toBe('2026-11-02');
    expect(addDays('2026-03-28', 2)).toBe('2026-03-30'); // across the DST change
    expect(daysInclusive('2026-11-02', '2026-11-11')).toBe(10);
  });
});

describe('sites in the demo API', () => {
  it('opens a site for a won deal, schedules work and computes workload', async () => {
    const a = createMockApi();
    const customer = (await a.listCounterparties(undefined, 'CUSTOMER')).find((c) => c.checkStatus === 'GREEN')!;
    const [crew] = await a.listCounterparties(undefined, 'SUBCONTRACTOR');
    let d = await a.createDeal({ title: 'Byt Žižkov', customerId: customer.id, direction: 'CONSTRUCTION', siteCountry: 'CZ',
      currency: 'CZK', defaultMarkupPct: 20 });
    d = await a.addLine(d.id, { kind: 'WORK', description: 'Malování', workType: 'FINISHING', quantity: 80, unitCost: 90,
      subcontractorId: crew.id });
    await a.addLine(d.id, { kind: 'MATERIAL', description: 'Barva', quantity: 1, unitCost: 4000 });
    await expect(a.createSite(d.id)).rejects.toThrow(/customer accepted/);
    await a.moveDeal(d.id, 'QUOTED');
    await a.moveDeal(d.id, 'WON');
    let s = await a.createSite(d.id);
    await expect(a.createSite(d.id)).rejects.toThrow(/already has a site/);
    await expect(a.tasksFromDeal(s.id)).rejects.toThrow(/planned start/);
    const start = '2027-03-01'; // a Monday far from the demo schedule
    s = await a.editSite(s.id, { ...s, address: 'Seifertova 10', city: 'Praha', country: 'cz', latitude: 50.0849,
      longitude: 14.4497, plannedStart: start, plannedEnd: '2027-03-12', status: 'ACTIVE' });
    expect(s.country).toBe('CZ');
    s = await a.tasksFromDeal(s.id);
    expect(s.tasks.map((t) => t.title)).toEqual(['Malování']);
    expect((await a.tasksFromDeal(s.id)).tasks).toHaveLength(1);
    s = await a.addTask(s.id, { title: 'Úklid', workType: null, subcontractorId: crew.id, start: '2027-03-08',
      end: '2027-03-08', progressPct: 100 });
    expect(s.progressPct).toBe(Math.round(100 / 13)); // 1 of 12 + 1 days
    await expect(a.addTask(s.id, { title: 'x', workType: null, subcontractorId: customer.id, start, end: start,
      progressPct: 0 })).rejects.toThrow(/not a subcontractor/);
    await expect(a.editSite(s.id, { ...s, plannedEnd: '2027-01-01' })).rejects.toThrow(/ends on or after/);

    expect((await a.listSites({ city: 'praha', workType: 'FINISHING' })).map((x) => x.id)).toEqual([s.id]);
    expect(await a.listSites({ country: 'XX' })).toEqual([]);
    const row = (await a.workload(start, '2027-03-14')).find((r) => r.subcontractor.id === crew.id)!;
    expect(row.weeks.map((w) => w.monday)).toEqual(['2027-03-01', '2027-03-08']);
    expect(row.weeks.map((w) => w.parallelTasks)).toEqual([1, 2]);
  });
});

describe('maps loader', () => {
  it('adds the Google script once, with the key, language and callback', async () => {
    const p1 = loadGoogleMaps('AIza-test key', 'cs');
    const p2 = loadGoogleMaps('AIza-test key', 'cs');
    expect(p1).toBe(p2);
    const scripts = [...document.querySelectorAll('script')].filter((s) => s.src.includes('maps.googleapis.com'));
    expect(scripts).toHaveLength(1);
    expect(scripts[0].src).toContain('key=AIza-test%20key');
    expect(scripts[0].src).toContain('language=cs');
    expect(scripts[0].src).toContain('callback=__dealosMapsReady');
    window.__dealosMapsReady!();
    await expect(p1).resolves.toBeUndefined();
  });
});

describe('site screens', () => {
  it('lists sites with a notice when no map key is configured', async () => {
    renderAt('/sites');
    expect(await screen.findByText('Hala magazynowa, Gliwice')).toBeInTheDocument();
    expect(screen.getByText('Fasáda, Wesel')).toBeInTheDocument();
    expect(await screen.findByText(/Карта Google не подключена/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Страна'), { target: { value: 'DE' } });
    await waitFor(() => expect(screen.queryByText('Hala magazynowa, Gliwice')).toBeNull());
    expect(screen.getByText('Fasáda, Wesel')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Сбросить фильтры'));
    expect(await screen.findByText('Hala magazynowa, Gliwice')).toBeInTheDocument();
  });

  it('shows the schedule and edits a task', async () => {
    renderAt('/sites');
    fireEvent.click(await screen.findByText('Fasáda, Wesel'));
    const table = await screen.findByRole('table', { name: 'График работ' });
    expect(within(table).getAllByText('Zateplení fasády ETICS').length).toBeGreaterThan(0);
    expect(within(table).getByText('сегодня')).toBeInTheDocument();
    fireEvent.click(within(table).getByRole('button', { name: /^Lešení:/ }));
    const dialog = screen.getByRole('dialog', { name: 'Изменить задачу' });
    fireEvent.change(within(dialog).getByLabelText('Готовность, %'), { target: { value: '150' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сохранить' }));
    expect(within(dialog).getByText('Целое число от 0 до 100.')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Готовность, %'), { target: { value: '90' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(await screen.findByText(/Просрочено задач: 1/)).toBeInTheDocument(); // the scaffolding ended last week
  });

  it('shows workload per week and the tasks behind a cell', async () => {
    renderAt('/workload');
    const cell = await screen.findByRole('button', { name: new RegExp(`Elektro Brno a.s., неделя .*: параллельных задач 2`) });
    fireEvent.click(cell);
    const detail = screen.getByRole('region', { name: /неделя с/ });
    expect(within(detail).getByText('Hala magazynowa, Gliwice')).toBeInTheDocument();
    expect(within(detail).getByText('Fasáda, Wesel')).toBeInTheDocument();
  });

  it('offers to open a site on a won deal', async () => {
    renderAt('/deals');
    fireEvent.click(await screen.findByText('Rekonstrukce kanceláří, Kraków'));
    fireEvent.click(await screen.findByRole('button', { name: 'Клиент согласился' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Открыть объект' }));
    expect(await screen.findByText('← Все объекты')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Rekonstrukce kanceláří, Kraków' })).toBeInTheDocument();
    expect(screen.getByText(/Задач пока нет/)).toBeInTheDocument();
  });
});
