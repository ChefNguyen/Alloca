import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { SidebarProvider } from '@/context/SidebarContext';
import { AppLayout } from '@/components/layout/AppLayout';
import { Login } from '@/pages/Login';
import { Dashboard } from '@/pages/Dashboard';
import { FindResource } from '@/pages/FindResource';
import { ResourceDetail } from '@/pages/ResourceDetail';
import { MyBookings } from '@/pages/MyBookings';
import { Analytics } from '@/pages/Analytics';
import { Administration } from '@/pages/Administration';

// Role-protected route wrapper
const RoleRoute: React.FC<{
  allowedRoles: string[];
  children: React.ReactElement;
}> = ({ allowedRoles, children }) => {
  const { role, isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!allowedRoles.includes(role)) return <Navigate to="/" replace />;
  return children;
};

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      {/* Main Authenticated Layout */}
      <Route path="/" element={<AppLayout />}>
        <Route index element={<Dashboard />} />
        <Route path="find-resource" element={<FindResource />} />
        <Route path="resources/:id" element={<ResourceDetail />} />
        <Route path="my-bookings" element={<MyBookings />} />

        {/* Manager & Admin Only */}
        <Route
          path="analytics"
          element={
            <RoleRoute allowedRoles={['MANAGER', 'ADMIN']}>
              <Analytics />
            </RoleRoute>
          }
        />

        {/* Admin Only */}
        <Route
          path="administration"
          element={
            <RoleRoute allowedRoles={['ADMIN']}>
              <Administration />
            </RoleRoute>
          }
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default function App() {
  return (
    <AuthProvider>
      <SidebarProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </SidebarProvider>
    </AuthProvider>
  );
}
