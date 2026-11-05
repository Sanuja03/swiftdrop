import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ROUTES } from './config/routes'
import CustomerLayout from './layouts/CustomerLayout'
import PublicLayout from './layouts/PublicLayout'
import NotFoundPage from './pages/NotFoundPage'
import CreateDeliveryPage from './pages/customer/CreateDeliveryPage'
import DeliveryHistoryPage from './pages/customer/DeliveryHistoryPage'
import LiveTrackingPage from './pages/customer/LiveTrackingPage'
import MyShipmentsPage from './pages/customer/MyShipmentsPage'
import OverviewPage from './pages/customer/OverviewPage'
import SettingsPage from './pages/customer/SettingsPage'
import TrackParcelPage from './pages/public/TrackParcelPage'

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
          <Route path="history" element={<DeliveryHistoryPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}
