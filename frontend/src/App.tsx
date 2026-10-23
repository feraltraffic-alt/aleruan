import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { I18nProvider } from './i18n';

// Each section loads when first opened, so the first screen appears faster.
const AnalyticsPage = lazy(() => import('./pages/Analytics').then((m) => ({ default: m.AnalyticsPage })));
const AuditPage = lazy(() => import('./pages/Audit').then((m) => ({ default: m.AuditPage })));
const CounterpartiesPage = lazy(() => import('./pages/Counterparties').then((m) => ({ default: m.CounterpartiesPage })));
const CounterpartyDetailPage = lazy(() => import('./pages/CounterpartyDetail').then((m) => ({ default: m.CounterpartyDetailPage })));
const ChangeOrderPage = lazy(() => import('./pages/ChangeOrderPage').then((m) => ({ default: m.ChangeOrderPage })));
const DashboardPage = lazy(() => import('./pages/Dashboard').then((m) => ({ default: m.DashboardPage })));
const DealDetailPage = lazy(() => import('./pages/DealDetail').then((m) => ({ default: m.DealDetailPage })));
const DealsPage = lazy(() => import('./pages/Deals').then((m) => ({ default: m.DealsPage })));
const OfferPageView = lazy(() => import('./pages/Offer').then((m) => ({ default: m.OfferPageView })));
const RfqDetailPage = lazy(() => import('./pages/RfqDetail').then((m) => ({ default: m.RfqDetailPage })));
const SiteDetailPage = lazy(() => import('./pages/SiteDetail').then((m) => ({ default: m.SiteDetailPage })));
const SitesPage = lazy(() => import('./pages/Sites').then((m) => ({ default: m.SitesPage })));
const WorkloadPage = lazy(() => import('./pages/Workload').then((m) => ({ default: m.WorkloadPage })));
const DefectsPage = lazy(() => import('./pages/Defects').then((m) => ({ default: m.DefectsPage })));
const InvoiceDetailPage = lazy(() => import('./pages/InvoiceDetail').then((m) => ({ default: m.InvoiceDetailPage })));
const InvoicesPage = lazy(() => import('./pages/Invoices').then((m) => ({ default: m.InvoicesPage })));
const MoneyExportPage = lazy(() => import('./pages/Invoices').then((m) => ({ default: m.MoneyExportPage })));
const ReferencePage = lazy(() => import('./pages/Reference').then((m) => ({ default: m.ReferencePage })));

export function AppRoutes() {
  return (
    <Suspense fallback={<p className="empty" aria-busy="true" />}>
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
        <Route path="invoices" element={<InvoicesPage />} />
        <Route path="invoices/money-s4" element={<MoneyExportPage />} />
        <Route path="invoices/:id" element={<InvoiceDetailPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="audit" element={<AuditPage />} />
        <Route path="reference" element={<ReferencePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
    </Suspense>
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
