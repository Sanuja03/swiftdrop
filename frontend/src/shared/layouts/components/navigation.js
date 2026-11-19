import { BarChart3, Bell, CirclePlus, Gauge, History, House, MapPinned, Package, Settings, Tags, Truck, Users } from 'lucide-react'
import { ROUTES } from '@/shared/config/routes'

/** Customer sidebar entries. */
export const CUSTOMER_NAVIGATION = [
  { label: 'Overview', to: ROUTES.app, icon: House, end: true },
  { label: 'My Shipments', to: ROUTES.shipments, icon: Package },
  { label: 'Create Delivery', to: ROUTES.createDelivery, icon: CirclePlus },
  { label: 'Delivery History', to: ROUTES.history, icon: History },
  { label: 'Settings', to: ROUTES.settings, icon: Settings },
]

/** Shared Dispatcher/Admin pages available in this branch. */
export const ADMIN_NAVIGATION = [
  { label: 'User Management', to: ROUTES.adminUsers, icon: Users, roles: ['ADMIN'] },
  { label: 'Reports', to: ROUTES.adminReports, icon: BarChart3, roles: ['ADMIN'] },
  { label: 'Zones', to: ROUTES.adminZones, icon: MapPinned, roles: ['ADMIN'] },
  { label: 'Pricing', to: ROUTES.adminPricing, icon: Tags, roles: ['ADMIN'] },
  { label: 'Dispatch Queue', to: ROUTES.dispatch, icon: Truck, end: true },
  { label: 'Courier Workloads', to: ROUTES.dispatchWorkload, icon: Gauge },
  { label: 'Notifications', to: ROUTES.notifications, icon: Bell },
]
