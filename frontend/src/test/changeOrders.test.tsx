import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { I18nProvider } from '../i18n';

describe('change orders in the demo API', () => {
  it('adds approved extras to the contract and keeps sent prices frozen', async () => {
    const a = createMockApi();
    const customer = (await a.listCounterparties(undefined, 'CUSTOMER')).find((c) => c.checkStatus === 'GREEN')!;
    let d = await a.createDeal({ title: 'Omítky', customerId: customer.id, direction: 'CONSTRUCTION', currency: 'CZK',
      defaultMarkupPct: 25 });
    d = await a.addLine(d.id, { kind: 'WORK', description: 'Omítka Q2', quantity: 100, unitCost: 400 });
    await expect(a.createChangeOrder(d.id, { title: 'Q3' })).rejects.toThrow(/customer accepted/);
    await a.moveDeal(d.id, 'QUOTED');
    await a.moveDeal(d.id, 'WON');
    let co = await a.createChangeOrder(d.id, { title: 'Omítka Q3', reason: 'Hladší povrch', extraDays: 3 });
    expect(co.number).toBe(`${d.number}-C1`);
    await expect(a.changeOrderAction(co.id, 'send')).rejects.toThrow(/at least one line/);
    co = await a.addChangeLine(co.id, { kind: 'WORK', description: 'Příplatek Q3', quantity: 100, unitCost: 80 });
    co = await a.addChangeLine(co.id, { kind: 'WORK', description: 'Q2 se nedělá v koupelně', quantity: -10, unitCost: 400 });
    expect(co.totals.price).toBe(10000 - 5000);
    co = await a.changeOrderAction(co.id, 'send');
    expect((await a.getDeal(d.id)).contract.pendingPrice).toBe(5000);
    await expect(a.updateChangeLine(co.id, co.lines[0].id, { ...co.lines[0], markupPct: null, quantity: 120, subcontractorId: null }))
      .rejects.toThrow(/revise/);
    co = await a.updateChangeLine(co.id, co.lines[0].id, { ...co.lines[0], markupPct: null, unitCost: 60, subcontractorId: null });
    expect(co.totals.price).toBe(5000);
    expect(co.totals.cost).toBe(6000 - 4000);
    await expect(a.decideChangeOrder(co.id, 'approve', ' ', null)).rejects.toThrow(/who decided/);
    co = await a.decideChangeOrder(co.id, 'approve', 'Ing. Petra Malá', 'telefonicky');
    const after = await a.getDeal(d.id);
    expect(after.contract).toMatchObject({ pendingPrice: 0, extraDays: 3 });
    expect(after.contract.total.price).toBe(55000);
    expect(after.totals.price).toBe(50000);
  });
});

describe('change order screens', () => {
  it('creates a change order from a won deal and records the approval', async () => {
    render(<I18nProvider><MemoryRouter initialEntries={['/sites']}><AppRoutes /></MemoryRouter></I18nProvider>);
    fireEvent.click(await screen.findByText('Fasáda, Wesel'));
    fireEvent.click(await screen.findByText(/^Сделка D-/));
    fireEvent.click(await screen.findByRole('button', { name: 'Новые доп. работы' }));
    let dialog = screen.getByRole('dialog', { name: 'Новые доп. работы' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Создать' }));
    expect(within(dialog).getByText('Опишите, что меняется.')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Что меняется'), { target: { value: 'Barva fasády navíc' } });
    fireEvent.change(within(dialog).getByLabelText('Влияние на срок, дней'), { target: { value: '2' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Создать' }));

    expect(await screen.findByRole('heading', { name: 'Barva fasády navíc' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Добавить позицию' }));
    dialog = screen.getByRole('dialog', { name: 'Добавить позицию' });
    fireEvent.change(within(dialog).getByLabelText('Описание'), { target: { value: 'Druhý nátěr' } });
    fireEvent.change(within(dialog).getByLabelText('Кол-во'), { target: { value: '400' } });
    fireEvent.change(within(dialog).getByLabelText('Себестоимость за ед., EUR'), { target: { value: '5' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Добавить позицию' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.getByText('Druhý nátěr')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Отправлено клиенту' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Клиент одобрил' }));
    dialog = screen.getByRole('dialog', { name: 'Клиент одобрил' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Записать одобрение' }));
    expect(within(dialog).getByText('Укажите, кто принял решение.')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Кто со стороны клиента'), { target: { value: 'Herr Krüger, Bauleiter' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Записать одобрение' }));
    expect(await screen.findByText(/Одобрено: Herr Krüger, Bauleiter/)).toBeInTheDocument();

    fireEvent.click(screen.getByText(/^← Сделка/));
    expect(await screen.findByText(/доп. работы 2\s?440,00/)).toBeInTheDocument(); // 400 × 5 × 1.22
    expect(screen.getByText(/срок 2 дн./)).toBeInTheDocument();
  });
});
