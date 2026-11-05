import { CirclePlus, History, House, Package, Settings } from 'lucide-react'
import { ROUTES } from '@/shared/config/routes'

/** Customer sidebar entries. */
export const CUSTOMER_NAVIGATION = [
  { label: 'Overview', to: ROUTES.app, icon: House, end: true },
  { label: 'My Shipments', to: ROUTES.shipments, icon: Package },
  { label: 'Create Delivery', to: ROUTES.createDelivery, icon: CirclePlus },
  { label: 'Delivery History', to: ROUTES.history, icon: History },
  { label: 'Settings', to: ROUTES.settings, icon: Settings },
]
