import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { I18nProvider } from '../i18n';

describe('acceptance stages in the demo API', () => {
  it('covers the contract and follows the handover rules', async () => {
    const a = createMockApi();
    const [site] = (await a.listSites({ city: 'Wesel' }));
    const full = await a.getSite(site.id);
    await expect(a.createStage(site.id, { title: ' ', description: null, amount: 1, plannedDate: null, taskIds: [] }))
      .rejects.toThrow(/title/);
    const s1 = await a.createStage(site.id, { title: 'Lešení a sever', description: null, amount: 10000,
      plannedDate: null, taskIds: [full.tasks[0].id] });
    let { coverage } = await a.stages(site.id);
    expect(coverage).toMatchObject({ contractValue: 17080, planned: 10000, accepted: 0, unassigned: 7080, currency: 'EUR' });
    await expect(a.decideStage(s1.id, { decision: 'ACCEPTED', by: 'x', remarks: null, fixUntil: null })).rejects.toThrow(/cannot move/);
    await a.stageAction(s1.id, 'submit');
    await expect(a.decideStage(s1.id, { decision: 'ACCEPTED_WITH_REMARKS', by: 'Herr Krüger', remarks: ' ', fixUntil: null }))
      .rejects.toThrow(/remarks/);
    await a.decideStage(s1.id, { decision: 'ACCEPTED_WITH_REMARKS', by: 'Herr Krüger', remarks: 'Sokl', fixUntil: '2026-12-01' });
    ({ coverage } = await a.stages(site.id));
    expect(coverage.accepted).toBe(10000);
    await expect(a.removeStage(s1.id)).rejects.toThrow(/planned/);
  });
});

describe('acceptance screen', () => {
  it('adds a stage by share of the contract, hands it over and records the decision', async () => {
    render(<I18nProvider><MemoryRouter initialEntries={['/sites']}><AppRoutes /></MemoryRouter></I18nProvider>);
    fireEvent.click(await screen.findByText('Hala magazynowa, Gliwice'));
    fireEvent.click(await screen.findByRole('tab', { name: 'Приёмка' }));
    const add = await screen.findByRole('button', { name: 'Добавить этап' });
    await waitFor(() => expect(add).toBeEnabled());
    fireEvent.click(add);
    const dialog = screen.getByRole('dialog', { name: 'Добавить этап' });
    fireEvent.change(within(dialog).getByLabelText('Этап'), { target: { value: 'Ocelová konstrukce' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '50 %' }));
    expect((within(dialog).getByLabelText(/Сумма без НДС/) as HTMLInputElement).value).not.toBe('');
    fireEvent.click(within(dialog).getByLabelText(/Montáž ocelové konstrukce/));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Добавить этап' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(await screen.findByText(/1\. Ocelová konstrukce/)).toBeInTheDocument();
    expect(screen.getByText(/не распределено/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Сдать клиенту' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Решение клиента' }));
    const decide = screen.getByRole('dialog', { name: 'Решение клиента' });
    fireEvent.click(within(decide).getByLabelText('Не принят'));
    fireEvent.change(within(decide).getByLabelText('Кто со стороны клиента'), { target: { value: 'p. Nowak' } });
    fireEvent.click(within(decide).getByRole('button', { name: 'Записать решение' }));
    expect(within(decide).getByText('Запишите замечания клиента.')).toBeInTheDocument();
    fireEvent.change(within(decide).getByLabelText('Почему не принял'), { target: { value: 'Chybí kotvy' } });
    fireEvent.click(within(decide).getByRole('button', { name: 'Записать решение' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(await screen.findByText('«Chybí kotvy»')).toBeInTheDocument();
    expect(screen.getByText('Не принят')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Сдать клиенту' })).toBeInTheDocument(); // can hand it over again
  });
});
