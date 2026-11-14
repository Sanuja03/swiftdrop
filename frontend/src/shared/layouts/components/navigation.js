import { CirclePlus, History, House, MapPinned, Package, Settings, Tags } from 'lucide-react'
import { ROUTES } from '@/shared/config/routes'

/** Customer sidebar entries. */
export const CUSTOMER_NAVIGATION = [
  { label: 'Overview', to: ROUTES.app, icon: House, end: true },
  { label: 'My Shipments', to: ROUTES.shipments, icon: Package },
  { label: 'Create Delivery', to: ROUTES.createDelivery, icon: CirclePlus },
  { label: 'Delivery History', to: ROUTES.history, icon: History },
  { label: 'Settings', to: ROUTES.settings, icon: Settings },
]

/** Admin sidebar entries. */
export const ADMIN_NAVIGATION = [
  { label: 'Zones', to: ROUTES.adminZones, icon: MapPinned },
  { label: 'Pricing', to: ROUTES.adminPricing, icon: Tags },
]
