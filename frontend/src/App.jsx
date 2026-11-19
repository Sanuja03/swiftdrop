import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

// Auth (identity & accounts)
import { AuthProvider, useAuth } from './context/AuthContext'
import ProtectedRoute from './components/common/ProtectedRoute'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import ProfilePage from './pages/ProfilePage'

// Customer app (booking, tracking, dashboard)
import { ROUTES } from '@/shared/config/routes'
import CustomerLayout from '@/shared/layouts/CustomerLayout'
import PublicLayout from '@/shared/layouts/PublicLayout'
import NotFoundPage from '@/shared/pages/NotFoundPage'
import { BookingConfirmationPage, CreateDeliveryPage } from '@/features/booking'
import { DeliveryHistoryPage, LiveTrackingPage, MyShipmentsPage, TrackParcelPage } from '@/features/tracking'
import { OverviewPage } from '@/features/dashboard'
import { SettingsPage } from '@/features/account'
import CourierPage from '@/features/courier/pages/CourierPage'

// Dispatcher operations (queue management & courier workloads)
import {
  DispatcherRoute,
  DispatcherLayout,
  DispatchManagementPage,
  CourierWorkloadPage,
} from '@/features/dispatch'

// Merchant portal (orders, API key, webhooks)
import MerchantPortal from './features/merchant/pages/MerchantPortal'

import AdminLayout from '@/shared/layouts/AdminLayout'
import ReportsDashboard from './features/reports/pages/ReportsDashboard'
import UserManagement from './features/users/pages/UserManagement'
import MyNotifications from './features/notifications/pages/MyNotifications'
import { AdminZonesPage, AdminPricingPage } from '@/features/admin'

function OperationsLayout() {
  const { user } = useAuth()
  return user?.role?.replace(/^ROLE_/, '') === 'DISPATCHER' ? <DispatcherLayout /> : <AdminLayout />
}

function NotificationsLayout() {
  const { user } = useAuth()
  const role = user?.role?.replace(/^ROLE_/, '')
  return ['ADMIN', 'DISPATCHER'].includes(role) ? <OperationsLayout /> : <CustomerLayout />
}

export function AppRoutes() {
  return (
    <Routes>
      {/* Public: landing and auth */}
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ForgotPasswordPage />} />

      {/* Public: parcel tracking */}
      <Route element={<PublicLayout />}>
        <Route path={ROUTES.track} element={<TrackParcelPage />} />
        <Route path={`${ROUTES.track}/:trackingNumber`} element={<TrackParcelPage />} />
      </Route>

      {/* Logged-in: profile */}
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />

      {/* Logged-in: customer app */}
      <Route
        path={ROUTES.app}
        element={
          <ProtectedRoute>
            <CustomerLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<OverviewPage />} />
        <Route path="shipments" element={<MyShipmentsPage />} />
        <Route path="shipments/:trackingNumber" element={<LiveTrackingPage />} />
        <Route path="create" element={<CreateDeliveryPage />} />
        <Route path="bookings/:trackingNumber" element={<BookingConfirmationPage />} />
        <Route path="history" element={<DeliveryHistoryPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>

      {/* Administrator workspace */}
      <Route
        path={ROUTES.admin}
        element={<ProtectedRoute roles={['ROLE_ADMIN']}><AdminLayout /></ProtectedRoute>}
      >
        <Route index element={<Navigate to={ROUTES.adminReports} replace />} />
        <Route path="reports" element={<ReportsDashboard />} />
        <Route path="users" element={<UserManagement />} />
        <Route path="zones" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AdminZonesPage /></ProtectedRoute>} />
        <Route path="pricing" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AdminPricingPage /></ProtectedRoute>} />
      </Route>

      {/* Notifications use the signed-in account's layout. */}
      <Route path="/notifications" element={<ProtectedRoute><NotificationsLayout /></ProtectedRoute>}>
        <Route index element={<MyNotifications />} />
      </Route>

      {/* Logged-in couriers only */}
      <Route path={ROUTES.courier} element={<ProtectedRoute roles={['ROLE_COURIER']}><CourierPage /></ProtectedRoute>} />
      <Route path={`${ROUTES.courier}/:parcelId`} element={<ProtectedRoute roles={['ROLE_COURIER']}><CourierPage /></ProtectedRoute>} />

      {/* Logged-in: dispatcher operations */}
      <Route
        path={ROUTES.dispatch}
        element={
          <DispatcherRoute>
            <OperationsLayout />
          </DispatcherRoute>
        }
      >
        <Route index element={<DispatchManagementPage />} />
        <Route path="workload" element={<CourierWorkloadPage />} />
      </Route>

      {/* Logged-in merchants only */}
      <Route
        path="/merchant/*"
        element={
          <ProtectedRoute roles={['ROLE_MERCHANT']}>
            <MerchantPortal />
          </ProtectedRoute>
        }
      />

      <Route path="/home" element={<Navigate to="/" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
