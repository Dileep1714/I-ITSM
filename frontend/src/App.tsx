import { Center, Loader } from '@mantine/core';
import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { Role } from './api/types';
import { useAuth } from './auth/AuthContext';
import { AppLayout } from './components/AppLayout';
import { DashboardPage } from './pages/DashboardPage';
import { IncidentListPage } from './pages/IncidentListPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { RegisterPage } from './pages/RegisterPage';
import { ReportIncidentPage } from './pages/ReportIncidentPage';
import { AuditLogPage } from './pages/admin/AuditLogPage';
import { OutboxPage } from './pages/admin/OutboxPage';
import { SettingsPage } from './pages/admin/SettingsPage';
import { SlaViolationsPage } from './pages/admin/SlaViolationsPage';
import { IncidentDetailPage } from './pages/incident/IncidentDetailPage';

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
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
