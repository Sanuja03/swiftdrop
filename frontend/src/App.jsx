import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

// Auth (identity & accounts)
import { AuthProvider } from './context/AuthContext'
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

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
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

          <Route path="/home" element={<Navigate to="/" replace />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}