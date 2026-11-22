import { Center, Loader } from '@mantine/core';
import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { Role } from './api/types';
import { useAuth } from './auth/AuthContext';
import { AppLayout } from './components/AppLayout';
import { DashboardPage } from './pages/DashboardPage';
import { IncidentListPage } from './pages/IncidentListPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ServiceMapPage } from './pages/ServiceMapPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ProfilePage } from './pages/ProfilePage';
import { RegisterPage } from './pages/RegisterPage';
import { ReportIncidentPage } from './pages/ReportIncidentPage';
import { AuditLogPage } from './pages/admin/AuditLogPage';
import { CatalogPage } from './pages/admin/CatalogPage';
import { OutboxPage } from './pages/admin/OutboxPage';
import { SettingsPage } from './pages/admin/SettingsPage';
import { SlaPoliciesPage } from './pages/admin/SlaPoliciesPage';
import { SlaViolationsPage } from './pages/admin/SlaViolationsPage';
import { UsersPage } from './pages/admin/UsersPage';
import { IncidentDetailPage } from './pages/incident/IncidentDetailPage';

// Reports pull in the charting library: load that code only when someone opens the page.
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((m) => ({ default: m.ReportsPage })));

/** Sends anonymous users to the login page, and users without the right role to the dashboard. */
function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <Center mih="100vh">
        <Loader />
      </Center>
    );
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="incidents" element={<IncidentListPage />} />
        <Route
          path="incidents/new"
          element={
            <RequireAuth roles={['EMPLOYEE']}>
              <ReportIncidentPage />
            </RequireAuth>
          }
        />
        <Route path="incidents/:id" element={<IncidentDetailPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="services" element={<ServiceMapPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route
          path="reports"
          element={
            <RequireAuth roles={['TECHNICIAN', 'ADMINISTRATOR']}>
              <Suspense fallback={<Center py="xl"><Loader /></Center>}>
                <ReportsPage />
              </Suspense>
            </RequireAuth>
          }
        />
        <Route path="admin/*" element={<RequireAuth roles={['ADMINISTRATOR']}><AdminRoutes /></RequireAuth>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

function AdminRoutes() {
  return (
    <Routes>
      <Route path="sla-violations" element={<SlaViolationsPage />} />
      <Route path="audit" element={<AuditLogPage />} />
      <Route path="outbox" element={<OutboxPage />} />
      <Route path="settings" element={<SettingsPage />} />
      <Route path="users" element={<UsersPage />} />
      <Route path="catalog" element={<CatalogPage />} />
      <Route path="sla-policies" element={<SlaPoliciesPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
