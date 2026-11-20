import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AdminPage } from '../features/admin/view/AdminPage';
import { ApplyPage } from '../features/apply/view/ApplyPage';
import { ClerkPage } from '../features/clerk/view/ClerkPage';
import { CrossingPage } from '../features/crossing/view/CrossingPage';
import { DashboardPage } from '../features/dashboard/view/DashboardPage';
import { EmployerPage } from '../features/employer/view/EmployerPage';
import { EnforcementPage } from '../features/enforcement/view/EnforcementPage';
import { AccessibilityPage } from '../features/home/view/AccessibilityPage';
import { HomePage } from '../features/home/view/HomePage';
import { PolicyPage } from '../features/policy/view/PolicyPage';
import { ProfilePage } from '../features/profile/view/ProfilePage';
import { queryClient } from '../shared/api/queryClient';
import { AuthProvider, useAuth } from '../shared/auth/AuthContext';
import { rolesFor } from '../shared/auth/nav';
import { Alert } from '../shared/ui';
import { Shell } from './Shell';

/** Route guard. The server enforces RBAC/ABAC anyway – this only avoids showing screens that would 403. */
function Guard({ path, children }: { path: string; children: ReactNode }) {
  const { session, ready, hasAny } = useAuth();
  const { t } = useTranslation();
  if (!ready) return <p role="status">{t('common.loading')}</p>;
  if (!session) return <Navigate to="/" replace />;
  if (!hasAny(...rolesFor(path))) return <Alert>{t('errors.forbidden')}</Alert>;
  return <>{children}</>;
}

const guarded: Array<[string, ReactNode]> = [
  ['/crossing', <CrossingPage key="c" />],
  ['/profile', <ProfilePage key="p" />],
  ['/apply', <ApplyPage key="a" />],
  ['/clerk', <ClerkPage key="k" />],
  ['/employer', <EmployerPage key="e" />],
  ['/policies', <PolicyPage key="o" />],
  ['/enforcement', <EnforcementPage key="n" />],
  ['/dashboard', <DashboardPage key="d" />],
  ['/admin', <AdminPage key="m" />],
];

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Shell />}>
              <Route index element={<HomePage />} />
              <Route path="/callback" element={<HomePage />} />
              <Route path="/accessibility" element={<AccessibilityPage />} />
              {guarded.map(([path, el]) => <Route key={path} path={path} element={<Guard path={path}>{el}</Guard>} />)}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
