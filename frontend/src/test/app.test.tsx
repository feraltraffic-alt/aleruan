import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../App';
import { I18nProvider } from '../i18n';

const renderAt = (path: string) => render(
  <I18nProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></I18nProvider>,
);

describe('app screens on demo data', () => {
  it('lists counterparties with status and roles', async () => {
    renderAt('/counterparties');
    expect(await screen.findByText('Stavby Horák s.r.o.')).toBeInTheDocument();
    const row = screen.getByText('Montage Rhein GmbH').closest('tr')!;
    expect(within(row).getByText('Риск')).toBeInTheDocument();
    expect(within(row).getByText('Заказчик')).toBeInTheDocument();
  });

  it('shows the dashboard risk summary and audit seal', async () => {
    renderAt('/');
    expect(await screen.findByText('Требуют внимания')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Цепочка цела')).toBeInTheDocument());
    expect(screen.getByRole('link', { name: 'Montage Rhein GmbH' })).toBeInTheDocument();
  });

  it('shows work types', async () => {
    renderAt('/reference');
    expect(await screen.findByText('Электромонтажные работы')).toBeInTheDocument();
  });

  it('form: checksum error appears, clears on edit, registry fills the name', async () => {
    renderAt('/counterparties');
    fireEvent.click(await screen.findByText('Добавить контрагента'));
    const id = screen.getByLabelText('IČO / NIP / рег. номер');
    fireEvent.change(id, { target: { value: '21590110' } });
    fireEvent.click(screen.getByText('Заполнить из реестра'));
    expect(screen.getByText('IČO не проходит проверку контрольной цифры.')).toBeInTheDocument();
    fireEvent.change(id, { target: { value: '21590109' } });
    expect(screen.queryByText('IČO не проходит проверку контрольной цифры.')).toBeNull();
    fireEvent.click(screen.getByText('Заполнить из реестра'));
    await waitFor(() => expect(screen.getByLabelText('Полное название')).toHaveValue('Demo firma 21590109 s.r.o.'));
  });
});
