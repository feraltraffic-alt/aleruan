import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { I18nProvider } from './i18n';
import { AuditPage } from './pages/Audit';
import { CounterpartiesPage } from './pages/Counterparties';
import { CounterpartyDetailPage } from './pages/CounterpartyDetail';
import { DashboardPage } from './pages/Dashboard';
import { ReferencePage } from './pages/Reference';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="counterparties" element={<CounterpartiesPage />} />
        <Route path="counterparties/:id" element={<CounterpartyDetailPage />} />
        <Route path="audit" element={<AuditPage />} />
        <Route path="reference" element={<ReferencePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export default function App() {
  return (
    <I18nProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </I18nProvider>
  );
}
