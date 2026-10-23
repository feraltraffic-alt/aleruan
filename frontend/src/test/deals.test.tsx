import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { api } from '../api';
import { I18nProvider } from '../i18n';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);
const tokenOf = (path: string) => path.replace('/offer/', '');

describe('deals and tenders in the demo API', () => {
  it('prices lines, freezes prices after the customer accepts, tender changes only the cost', async () => {
    const a = createMockApi();
    const customer = (await a.listCounterparties(undefined, 'CUSTOMER')).find((c) => c.checkStatus === 'GREEN')!;
    const [sub1, sub2] = await a.listCounterparties(undefined, 'SUBCONTRACTOR');

    await expect(a.createDeal({ title: 'x', customerId: sub1.id, direction: 'IT', currency: 'EUR', defaultMarkupPct: 10 }))
      .rejects.toThrow(/CUSTOMER/);
    let d = await a.createDeal({ title: 'Omítky', customerId: customer.id, direction: 'CONSTRUCTION', siteCountry: 'de',
      currency: 'EUR', defaultMarkupPct: 30 });
    expect(d.number).toMatch(/^D-\d{4}-\d{4}$/);
    expect(d.siteCountry).toBe('DE');
    await expect(a.moveDeal(d.id, 'QUOTED')).rejects.toThrow(/at least one line/);

    d = await a.addLine(d.id, { kind: 'WORK', description: 'Omítka', unit: 'm2', quantity: 100, unitCost: 50 });
    expect(d.totals).toMatchObject({ cost: 5000, price: 6500, margin: 1500 });
    d = await a.updateLine(d.id, d.lines[0].id, { ...d.lines[0], fixedUnitPrice: 52, markupPct: null, subcontractorId: null });
    expect(d.warnings).toContain('MARGIN_BELOW_THRESHOLD');
    expect(d.lines[0].markupPct).toBe(4);
    d = await a.updateLine(d.id, d.lines[0].id, { ...d.lines[0], fixedUnitPrice: null, markupPct: null, subcontractorId: null });
    const lineId = d.lines[0].id;

    await a.moveDeal(d.id, 'QUOTED');
    d = await a.moveDeal(d.id, 'WON');
    expect(d.pricesEditable).toBe(false);
    await expect(a.addLine(d.id, { kind: 'WORK', description: 'more', quantity: 1, unitCost: 1 })).rejects.toThrow(/frozen/);

    const rfq = await a.createRfq(d.id, { lineIds: [lineId] });
    const i1 = await a.invite(rfq.id, sub1.id, false);
    const i2 = await a.invite(rfq.id, sub2.id, false);
    await expect(a.invite(rfq.id, sub1.id, false)).rejects.toThrow(/already invited/);

    const page = await a.openOffer(tokenOf(i1.offerPath));
    expect(page.items).toHaveLength(1);
    expect(JSON.stringify(page)).not.toContain('6500'); // the subcontractor never sees the customer price
    const itemId = page.items[0].id;
    await a.submitOffer(tokenOf(i1.offerPath), { [itemId]: 47 }, 'Do 3 týdnů');
    await a.submitOffer(tokenOf(i2.offerPath), { [itemId]: 45 }, '');

    let view = await a.getRfq(rfq.id);
    expect(view.cheapestInvitationId).toBe(i2.invitation.id);
    expect(view.invitations.find((i) => i.id === i1.invitation.id)?.viewedAt).not.toBeNull();
    view = await a.award(rfq.id, i2.invitation.id);
    expect(view.status).toBe('AWARDED');

    d = await a.getDeal(d.id);
    expect(d.totals).toMatchObject({ cost: 4500, price: 6500, margin: 2000 });
    expect(d.lines[0].subcontractor?.id).toBe(sub2.id);
    await expect(a.openOffer('not-a-token')).rejects.toThrow(/not valid/);
    await expect(a.submitOffer(tokenOf(i1.offerPath), { [itemId]: 40 }, '')).rejects.toThrow(/no longer/);
  });

  it('filters tender candidates by work type and rejects past deadlines', async () => {
    const a = createMockApi();
    const [deal] = await a.listDeals();
    const full = await a.getDeal(deal.id);
    const rfq = await a.createRfq(deal.id, { lineIds: [full.lines[0].id] });
    const cands = await a.rfqCandidates('DISMANTLING');
    expect(cands.map((c) => c.legalName)).toEqual(['Stavby Horák s.r.o.']);
    await expect(a.createRfq(deal.id, { lineIds: [full.lines[0].id], deadline: '2000-01-01' })).rejects.toThrow(/past/);
    expect(rfq.items[0].description).toBe(full.lines[0].description);
  });
});

describe('deal screens', () => {
  it('lists the demo deal and opens it with totals and lines', async () => {
    renderAt('/deals');
    fireEvent.click(await screen.findByText('Rekonstrukce kanceláří, Kraków'));
    const totals = await screen.findByRole('region', { name: 'Итоги сделки' });
    expect(within(totals).getByText(/20\s?604,70/)).toBeInTheDocument();
    expect(within(totals).getByText(/4\s?054,70/)).toBeInTheDocument();
    expect(screen.getByText('Sádrokartonové příčky')).toBeInTheDocument();
    expect(screen.getByText('от цены')).toBeInTheDocument();
  });

  it('adds a line with a live preview of price and margin', async () => {
    renderAt('/deals');
    fireEvent.click(await screen.findByText('Rekonstrukce kanceláří, Kraków'));
    fireEvent.click(await screen.findByRole('button', { name: 'Добавить позицию' }));
    const dialog = screen.getByRole('dialog', { name: 'Добавить позицию' });
    fireEvent.change(within(dialog).getByLabelText('Описание'), { target: { value: 'Malování' } });
    fireEvent.change(within(dialog).getByLabelText('Кол-во'), { target: { value: '200' } });
    fireEvent.change(within(dialog).getByLabelText('Себестоимость за ед., EUR'), { target: { value: '4' } });
    expect(within(dialog).getByText(/1\s?000,00/)).toBeInTheDocument(); // 200 × 5.00 at the deal's 25 %
    fireEvent.click(within(dialog).getByLabelText('Фиксированная цена'));
    fireEvent.change(within(dialog).getByLabelText('Цена клиенту за ед., EUR'), { target: { value: '3,5' } });
    expect(within(dialog).getByText(/не больше 2,80/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Добавить позицию' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.getByText('Malování')).toBeInTheDocument();
    expect(screen.getByText('Есть позиции дешевле себестоимости.')).toBeInTheDocument();
  });

  it('offer page: subcontractor fills prices and sends the offer', async () => {
    const [deal] = await api.listDeals();
    const full = await api.getDeal(deal.id);
    const rfq = await api.createRfq(deal.id, { lineIds: [full.lines[0].id, full.lines[1].id] });
    const sub = (await api.rfqCandidates()).find((c) => c.legalName === 'Stavby Horák s.r.o.')!;
    const { offerPath } = await api.invite(rfq.id, sub.id, false);

    renderAt(offerPath);
    expect(await screen.findByText('Запрос цен. Получатель: Stavby Horák s.r.o.')).toBeInTheDocument();
    const inputs = screen.getAllByRole('textbox', { name: /Цена за единицу/ });
    fireEvent.change(inputs[0], { target: { value: 'abc' } });
    fireEvent.click(screen.getByRole('button', { name: 'Отправить предложение' }));
    expect(screen.getByText('Цена — число, ноль или больше.')).toBeInTheDocument();
    fireEvent.change(inputs[0], { target: { value: '9' } });
    fireEvent.change(inputs[1], { target: { value: '22,5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Отправить предложение' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Предложение отправлено');
    const view = await api.getRfq(rfq.id);
    expect(view.invitations[0].total).toBe(240 * 9 + 180 * 22.5);
  });

  it('offer page with a broken link explains what to do', async () => {
    renderAt('/offer/broken');
    expect(await screen.findByText('Ссылка не работает')).toBeInTheDocument();
  });
});

describe('editing a deal keeps what was not changed', () => {
  it('changing the markup keeps the site country (it drives the VAT suggestion)', async () => {
    renderAt('/deals');
    fireEvent.click(await screen.findByText('Rekonstrukce kanceláří, Kraków'));
    fireEvent.click(await screen.findByRole('button', { name: 'изменить' }));
    fireEvent.change(screen.getByLabelText('Наценка на себестоимость, %'), { target: { value: '27' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(screen.queryByLabelText('Наценка на себестоимость, %')).toBeNull());
    const facts = screen.getByText('Страна объекта').closest('div')!;
    expect(within(facts).getByText('PL')).toBeInTheDocument();
  });
});
