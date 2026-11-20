import { BarChart3, Bell, Calculator, CirclePlus, Gauge, History, House, MapPinned, Package, Settings, ShieldCheck, Tags, Truck, Users, Webhook } from 'lucide-react'
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

/** Merchant sidebar entries. */
export const MERCHANT_NAVIGATION = [
  { label: 'Dashboard', icon: BarChart3, tab: 'dashboard' },
  { label: 'My Orders', icon: Package, tab: 'orders' },
  { label: 'Book Delivery', icon: CirclePlus, tab: 'create-order' },
  { label: 'Fee Calculator', icon: Calculator, tab: 'calculator' },
  { label: 'Store Integration', icon: Webhook, tab: 'webhooks' },
  { label: 'Business Profile', icon: ShieldCheck, tab: 'registration' },
]
