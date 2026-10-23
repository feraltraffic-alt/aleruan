import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { I18nProvider } from './i18n';
import { AuditPage } from './pages/Audit';
import { CounterpartiesPage } from './pages/Counterparties';
import { CounterpartyDetailPage } from './pages/CounterpartyDetail';
import { ChangeOrderPage } from './pages/ChangeOrderPage';
import { DashboardPage } from './pages/Dashboard';
import { DealDetailPage } from './pages/DealDetail';
import { DealsPage } from './pages/Deals';
import { OfferPageView } from './pages/Offer';
import { RfqDetailPage } from './pages/RfqDetail';
import { SiteDetailPage } from './pages/SiteDetail';
import { SitesPage } from './pages/Sites';
import { WorkloadPage } from './pages/Workload';
import { DefectsPage } from './pages/Defects';
import { ReferencePage } from './pages/Reference';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="offer/:token" element={<OfferPageView />} />
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="counterparties" element={<CounterpartiesPage />} />
        <Route path="counterparties/:id" element={<CounterpartyDetailPage />} />
        <Route path="deals" element={<DealsPage />} />
        <Route path="deals/:id" element={<DealDetailPage />} />
        <Route path="change-orders/:id" element={<ChangeOrderPage />} />
        <Route path="rfqs/:id" element={<RfqDetailPage />} />
        <Route path="sites" element={<SitesPage />} />
        <Route path="sites/:id" element={<SiteDetailPage />} />
        <Route path="workload" element={<WorkloadPage />} />
        <Route path="defects" element={<DefectsPage />} />
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
