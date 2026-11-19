import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AppRoutes } from '@/App'

let role = 'ROLE_ADMIN'
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({
  user: { id: 'test-user', role, fullName: 'Test Operations User' }, isAuthenticated: true, loading: false, logout: vi.fn(),
}) }))
vi.mock('@react-oauth/google', () => ({ GoogleLogin: () => null }))
vi.mock('@/features/notifications/services/notificationApi', () => ({ getUnreadCount: vi.fn(async () => 0) }))
vi.mock('@/features/admin/api/adminApi', () => ({
  getAllZones: vi.fn(async () => []), getPricingRules: vi.fn(async () => []),
  createZone: vi.fn(), updateZone: vi.fn(), setZoneActive: vi.fn(), updatePricingRule: vi.fn(),
}))
vi.mock('@/features/dispatch/api/dispatchApi', () => ({
  getUnassignedParcels: vi.fn(async () => []), getActiveCouriers: vi.fn(async () => []),
  getCourierWorkloads: vi.fn(async () => []), assignCourier: vi.fn(), cancelAssignment: vi.fn(),
}))
vi.mock('@/features/users/services/userManagementApi', () => ({
  getAllUsers: vi.fn(async () => []), suspendUser: vi.fn(), restoreUser: vi.fn(),
}))
vi.mock('@/features/users/services/merchantApprovalApi', () => ({
  getMerchantApplications: vi.fn(async () => []), approveMerchantApplication: vi.fn(), rejectMerchantApplication: vi.fn(),
}))

beforeEach(() => { role = 'ROLE_ADMIN' })

for (const path of ['/courier/deliveries', '/courier/deliveries/test-parcel', '/merchant']) {
  it(`preserves the team's role restriction on ${path}`, async () => {
    role = 'ROLE_CUSTOMER'
    render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>)
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission')
  })
}

it('connects all integrated operations pages and opens the existing Zones/Pricing screens for admins', async () => {
  render(<MemoryRouter initialEntries={['/admin/zones']}><AppRoutes /></MemoryRouter>)
  expect(await screen.findByRole('heading', { name: 'Zones', exact: true })).toBeInTheDocument()
  for (const name of ['User Management', 'Reports', 'Zones', 'Pricing', 'Dispatch Queue', 'Courier Workloads']) {
    expect(screen.getByRole('link', { name, exact: true })).toBeInTheDocument()
  }
  expect(screen.getByRole('link', { name: 'Notifications', exact: true })).toHaveAttribute('href', '/notifications')
  expect(screen.getByRole('link', { name: 'Dispatch Queue' })).toHaveAttribute('href', '/dispatch')
  expect(screen.getByRole('link', { name: 'Courier Workloads' })).toHaveAttribute('href', '/dispatch/workload')
  fireEvent.click(screen.getByRole('link', { name: 'Pricing', exact: true }))
  expect(await screen.findByRole('heading', { name: 'Pricing', exact: true })).toBeInTheDocument()
})

for (const path of ['/admin', '/admin/users', '/admin/reports', '/admin/zones', '/admin/pricing']) {
  it(`denies dispatcher direct access to ${path} and hides restricted menu links`, async () => {
    role = 'ROLE_DISPATCHER'
    render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>)
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission')
    expect(screen.queryByRole('link', { name: 'Zones', exact: true })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Pricing', exact: true })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'User Management' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Reports' })).not.toBeInTheDocument()
  })
}

for (const operationsRole of ['ROLE_ADMIN', 'ROLE_DISPATCHER']) {
  for (const [path, heading] of [['/dispatch', 'Dispatch Queue'], ['/dispatch/workload', 'Courier Workloads']]) {
    it(`keeps shared navigation and topbar on ${path} for ${operationsRole}`, async () => {
      role = operationsRole
      render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>)
      expect(await screen.findByRole('heading', { name: heading, exact: true })).toBeInTheDocument()
      expect(screen.getByRole('navigation', { name: operationsRole === 'ROLE_ADMIN' ? 'Admin' : 'Dispatch Operations', exact: true })).toBeInTheDocument()
      if (operationsRole === 'ROLE_ADMIN') {
        expect(screen.getByRole('button', { name: /^Notifications/ })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Zones', exact: true })).toBeInTheDocument()
        fireEvent.click(screen.getByRole('link', { name: 'User Management', exact: true }))
        expect(await screen.findByRole('heading', { name: 'User Management', exact: true })).toBeInTheDocument()
      } else {
        expect(screen.queryByRole('link', { name: 'Zones', exact: true })).not.toBeInTheDocument()
        expect(screen.queryByRole('link', { name: 'Pricing', exact: true })).not.toBeInTheDocument()
        expect(screen.queryByRole('link', { name: 'Reports', exact: true })).not.toBeInTheDocument()
        expect(screen.queryByRole('link', { name: 'User Management', exact: true })).not.toBeInTheDocument()
        expect(screen.getByText('Operations Portal')).toBeInTheDocument()
        expect(screen.getByText('DISPATCHER')).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Customer View' })).toHaveAttribute('href', '/app')
      }
    })
  }
}
