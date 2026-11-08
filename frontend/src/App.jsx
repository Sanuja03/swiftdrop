import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
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
    <BrowserRouter>
      <Routes>
        <Route path={ROUTES.home} element={<Navigate to={ROUTES.track} replace />} />
        <Route element={<PublicLayout />}>
          <Route path={ROUTES.track} element={<TrackParcelPage />} />
          <Route path={`${ROUTES.track}/:trackingNumber`} element={<TrackParcelPage />} />
        </Route>
        <Route path={ROUTES.app} element={<CustomerLayout />}>
          <Route index element={<OverviewPage />} />
          <Route path="shipments" element={<MyShipmentsPage />} />
          <Route path="shipments/:trackingNumber" element={<LiveTrackingPage />} />
          <Route path="create" element={<CreateDeliveryPage />} />
          <Route path="bookings/:trackingNumber" element={<BookingConfirmationPage />} />
          <Route path="history" element={<DeliveryHistoryPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}
