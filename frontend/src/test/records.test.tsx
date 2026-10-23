import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import { createMockApi } from '../api/mock';
import { addDays, localToday } from '../dates';
import { I18nProvider } from '../i18n';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);
const jpeg = () => new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 16, 74, 70, 73, 70, 0, 1])], 'IMG_1.jpg', { type: 'image/jpeg' });

describe('diary, defects and warranty in the demo API', () => {
  it('follows the same rules as the backend', async () => {
    const a = createMockApi();
    const [site] = await a.listSites({ city: 'Gliwice' });
    const today = localToday();
    await expect(a.addDiaryEntry(site.id, { entryDate: addDays(today, 1), weather: null, workers: 3, text: 'x', correctsId: null }))
      .rejects.toThrow(/today or an earlier day/);
    const e = await a.addDiaryEntry(site.id, { entryDate: today, weather: 'Jasno', workers: 6, text: ' Montáž vazníků ', correctsId: null });
    expect(e).toMatchObject({ text: 'Montáž vazníků', editable: true });
    const fix = await a.addDiaryEntry(site.id, { entryDate: today, weather: null, workers: 7, text: 'Oprava: 7 lidí', correctsId: e.id });
    expect((await a.diary(site.id)).find((x) => x.id === e.id)?.correctedBy).toEqual([fix.id]);
    const photo = await a.uploadPhoto('diary', e.id, jpeg(), 'IMG_1.jpg');
    expect(photo.contentType).toBe('image/jpeg');
    await expect(a.uploadPhoto('diary', e.id, new File(['<html>'], 'x.html', { type: 'text/html' }), 'x.html'))
      .rejects.toThrow(/Only photos/);

    const [crew] = await a.listCounterparties(undefined, 'SUBCONTRACTOR');
    const d = await a.reportDefect(site.id, { title: 'Chybí kotvy', description: null, location: 'osa B3', severity: 'CRITICAL',
      subcontractorId: crew.id, stageId: null, foundOn: today, dueDate: null });
    expect(d).toMatchObject({ seq: 1, dueDate: addDays(today, 2), warranty: false, nextStatuses: ['FIXED', 'DISMISSED'] });
    await expect(a.moveDefect(d.id, 'DISMISSED', null)).rejects.toThrow(/Say why/);
    await a.moveDefect(d.id, 'FIXED', 'Doplněno');
    await expect(a.moveDefect(d.id, 'OPEN', '')).rejects.toThrow(/Say why/);
    expect((await a.moveDefect(d.id, 'CLOSED', null)).status).toBe('CLOSED');

    const handed = await a.setHandover(site.id, addDays(today, -1), null);
    expect(handed.warrantyMonths).toBe(24);
    const claim = await a.reportDefect(site.id, { title: 'Zatéká', description: null, location: null, severity: 'MAJOR',
      subcontractorId: null, stageId: null, foundOn: today, dueDate: null });
    expect(claim.warranty).toBe(true);
    expect((await a.getSite(site.id)).openDefects).toBe(1);
    expect((await a.unresolvedDefects()).map((x) => x.id)).toEqual([claim.id]);
  });
});

describe('site records screens', () => {
  it('writes a diary entry and attaches a photo', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:photo');
    URL.revokeObjectURL = vi.fn();
    renderAt('/sites');
    fireEvent.click(await screen.findByText('Fasáda, Wesel'));
    fireEvent.click(await screen.findByRole('tab', { name: 'Стройжурнал' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Новая запись' }));
    const form = screen.getByRole('form', { name: 'Новая запись' });
    fireEvent.click(within(form).getByRole('button', { name: 'Сохранить' }));
    expect(within(form).getByText('Опишите, что происходило на объекте.')).toBeInTheDocument();
    fireEvent.change(within(form).getByLabelText('Людей на объекте'), { target: { value: '5' } });
    fireEvent.change(within(form).getByLabelText(/Что сделано/), { target: { value: 'Kotvení izolace, severní stěna' } });
    fireEvent.click(within(form).getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(screen.queryByRole('form', { name: 'Новая запись' })).toBeNull());
    const entry = (await screen.findByText('Kotvení izolace, severní stěna')).closest('article')!;
    expect(within(entry).getByText('людей: 5')).toBeInTheDocument();
    fireEvent.change(within(entry).getByLabelText('Добавить фото'), { target: { files: [jpeg()] } });
    expect(await within(entry).findByRole('link', { name: 'Открыть IMG_1.jpg' })).toBeInTheDocument();
  });

  it('reports a defect, moves it through fixing and shows it in the overview', async () => {
    renderAt('/sites');
    fireEvent.click(await screen.findByText('Fasáda, Wesel'));
    fireEvent.click(await screen.findByRole('tab', { name: 'Дефекты' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Записать дефект' }));
    const dialog = screen.getByRole('dialog', { name: 'Записать дефект' });
    fireEvent.change(within(dialog).getByLabelText('Что не так'), { target: { value: 'Nedotažené kotvy' } });
    fireEvent.click(within(dialog).getByLabelText('Критический'));
    expect(within(dialog).getByText(/2 дня/)).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Кто исправляет'), {
      target: { value: (await within(dialog).findByRole('option', { name: /Stavby Horák s\.r\.o\. — работает на объекте/ }) as HTMLOptionElement).value },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Записать дефект' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    const card = (await screen.findByText('Nedotažené kotvy')).closest('article')!;
    expect(within(card).getByText('отвечает Stavby Horák s.r.o.')).toBeInTheDocument();
    expect(await screen.findByRole('tab', { name: /Дефекты\s*1/ })).toBeInTheDocument();

    fireEvent.click(within(card).getByRole('button', { name: 'Исправлено' }));
    fireEvent.change(within(card).getByLabelText('Что сделано (необязательно)'), { target: { value: 'Dotaženo' } });
    fireEvent.click(within(card).getByRole('button', { name: 'Подтвердить: Исправлено' }));
    expect(await within(card).findByText('Исправлен, ждёт проверки')).toBeInTheDocument();
    fireEvent.click(within(card).getByRole('button', { name: 'Открыть снова' }));
    fireEvent.click(within(card).getByRole('button', { name: 'Подтвердить: Открыть снова' }));
    expect(within(card).getByText('Укажите причину.')).toBeInTheDocument();
  });

  it('records the handover and the warranty end', async () => {
    renderAt('/sites');
    fireEvent.click(await screen.findByText('Hala magazynowa, Gliwice'));
    fireEvent.click(await screen.findByRole('button', { name: 'Отметить сдачу' }));
    const dialog = screen.getByRole('dialog', { name: 'Отметить сдачу' });
    fireEvent.change(within(dialog).getByLabelText('Гарантия, месяцев'), { target: { value: '200' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сохранить' }));
    expect(within(dialog).getByText('Целое число месяцев от 0 до 120.')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Гарантия, месяцев'), { target: { value: '36' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сохранить' }));
    expect(await screen.findByText(/гарантия до/)).toBeInTheDocument();
  });
});
