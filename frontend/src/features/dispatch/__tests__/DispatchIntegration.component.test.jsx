import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AppRoutes } from '@/App'
import * as dispatchApi from '../api/dispatchApi'

// Mock Google OAuth to avoid provider requirement when redirecting to /login
vi.mock('@react-oauth/google', () => ({
  GoogleLogin: () => <button>Sign in with Google</button>,
  useGoogleLogin: () => vi.fn(),
  GoogleOAuthProvider: ({ children }) => children,
}))

// Mock dispatch APIs
vi.mock('../api/dispatchApi', () => ({
  getUnassignedParcels: vi.fn(),
  getActiveCouriers: vi.fn(),
  getCourierWorkloads: vi.fn(),
  assignCourier: vi.fn(),
  cancelAssignment: vi.fn(),
}))

// Mock AuthContext
let mockAuthState = {
  isAuthenticated: true,
  user: { fullName: 'Sunil Dispatcher', role: 'ROLE_DISPATCHER', email: 'sunil@swiftdrop.lk' },
  loading: false,
  logout: vi.fn(),
}

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => mockAuthState,
}))

const mockParcels = [
  {
    id: 'p-1',
    trackingNumber: 'SDP-INT-001',
    senderName: 'Sender A',
    recipientName: 'Recipient B',
    recipientPhone: '0770000000',
    pickupAddress: '10 Main Rd',
    pickupCity: 'Colombo',
    dropoffAddress: '20 Beach Rd',
    dropoffCity: 'Galle',
    serviceType: 'EXPRESS',
    weightKg: 2.0,
    fee: 450.0,
    status: 'CREATED',
    estimatedDeliveryAt: '2026-11-20T10:00:00Z',
    createdAt: '2026-11-14T09:00:00Z',
  },
]

const mockCouriers = [
  {
    id: 'c-1',
    fullName: 'Courier Kamal',
    email: 'kamal@swiftdrop.lk',
    phone: '0711112233',
  },
]

const mockWorkloads = [
  {
    courierId: 'c-1',
    courierName: 'Courier Kamal',
    courierEmail: 'kamal@swiftdrop.lk',
    courierPhone: '0711112233',
    activeAssignmentsCount: 1,
    workloadStatus: 'MODERATE',
  },
]

describe('Dispatch & Workload Routes - End-to-End Route Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    dispatchApi.getUnassignedParcels.mockResolvedValue(mockParcels)
    dispatchApi.getActiveCouriers.mockResolvedValue(mockCouriers)
    dispatchApi.getCourierWorkloads.mockResolvedValue(mockWorkloads)
  })

  it('1. /dispatch route renders DispatchManagementPage inside DispatcherLayout for ROLE_DISPATCHER', async () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Saman Silva', role: 'ROLE_DISPATCHER', email: 'saman@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <AppRoutes />
      </MemoryRouter>
    )

    // Verify DispatcherLayout shell elements are rendered
    expect(screen.getByRole('navigation', { name: 'Dispatch Operations' })).toBeInTheDocument()
    expect(screen.getByText('Dispatch Operations')).toBeInTheDocument()
    expect(screen.getByText('Saman Silva')).toBeInTheDocument()
    expect(screen.getByText('DISPATCHER')).toBeInTheDocument()

    // Verify DispatchManagementPage content
    expect(screen.getByRole('heading', { name: 'Dispatch Queue' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('SDP-INT-001')).toBeInTheDocument()
    })
  })

  it('2. /dispatch/workload route renders CourierWorkloadPage inside DispatcherLayout for ROLE_DISPATCHER', async () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Saman Silva', role: 'ROLE_DISPATCHER', email: 'saman@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch/workload']}>
        <AppRoutes />
      </MemoryRouter>
    )

    // Verify DispatcherLayout shell elements
    expect(screen.getByRole('navigation', { name: 'Dispatch Operations' })).toBeInTheDocument()

    // Verify CourierWorkloadPage content
    expect(screen.getByRole('heading', { name: 'Courier Workloads' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('Courier Kamal')).toBeInTheDocument()
    })
    expect(screen.getByText('MODERATE')).toBeInTheDocument()
  })

  it('3. Allows ROLE_ADMIN to access /dispatch and displays ADMIN badge', async () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Thenuri Admin', role: 'ROLE_ADMIN', email: 'thenuri@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <AppRoutes />
      </MemoryRouter>
    )

    // Topbar displays ADMIN badge
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(screen.getByText('Thenuri Admin')).toBeInTheDocument()

    // Page content rendered
    expect(screen.getByRole('heading', { name: 'Dispatch Queue' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('SDP-INT-001')).toBeInTheDocument()
    })
  })

  it('4. Allows ROLE_ADMIN to access /dispatch/workload', async () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Thenuri Admin', role: 'ROLE_ADMIN', email: 'thenuri@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch/workload']}>
        <AppRoutes />
      </MemoryRouter>
    )

    expect(screen.getByRole('heading', { name: 'Courier Workloads' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('Courier Kamal')).toBeInTheDocument()
    })
  })

  it('5. Denies ROLE_CUSTOMER with 403 Access Denied screen on /dispatch', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Customer Hiruni', role: 'ROLE_CUSTOMER', email: 'hiruni@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <AppRoutes />
      </MemoryRouter>
    )

    expect(screen.queryByRole('heading', { name: 'Dispatch Queue' })).not.toBeInTheDocument()
    expect(screen.getByText('403 - Access Denied')).toBeInTheDocument()
    expect(screen.getByText('CUSTOMER')).toBeInTheDocument()
    expect(screen.getByText('Back to Customer Portal')).toBeInTheDocument()
  })

  it('6. Denies ROLE_COURIER with 403 Access Denied screen on /dispatch/workload', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Courier Bimakshi', role: 'ROLE_COURIER', email: 'bimakshi@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch/workload']}>
        <AppRoutes />
      </MemoryRouter>
    )

    expect(screen.queryByRole('heading', { name: 'Courier Workloads' })).not.toBeInTheDocument()
    expect(screen.getByText('403 - Access Denied')).toBeInTheDocument()
    expect(screen.getByText('COURIER')).toBeInTheDocument()
  })

  it('7. Denies ROLE_MERCHANT with 403 Access Denied screen', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Merchant Retailer', role: 'ROLE_MERCHANT', email: 'merchant@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <AppRoutes />
      </MemoryRouter>
    )

    expect(screen.getByText('403 - Access Denied')).toBeInTheDocument()
    expect(screen.getByText('MERCHANT')).toBeInTheDocument()
  })

  it('8. Unauthenticated user on /dispatch is redirected to /login', () => {
    mockAuthState = {
      isAuthenticated: false,
      user: null,
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <AppRoutes />
      </MemoryRouter>
    )

    // Should redirect to LoginPage, not show dispatch content or 403
    expect(screen.queryByText('Dispatch Queue')).not.toBeInTheDocument()
    expect(screen.queryByText('403 - Access Denied')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Sign in to SwiftDrop|Welcome back/i })).toBeInTheDocument()
  })

  it('9. Navigates between Dispatch Queue and Courier Workloads using Sidebar links', async () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Saman Silva', role: 'ROLE_DISPATCHER', email: 'saman@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <AppRoutes />
      </MemoryRouter>
    )

    // Initially at /dispatch
    expect(screen.getByRole('heading', { name: 'Dispatch Queue' })).toBeInTheDocument()

    // Click Courier Workloads in sidebar
    const workloadLink = screen.getByRole('link', { name: /Courier Workloads/i })
    fireEvent.click(workloadLink)

    // Transitions to Courier Workloads page
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Courier Workloads' })).toBeInTheDocument()
    })

    // Click Dispatch Queue link to return
    const dispatchLink = screen.getByRole('link', { name: /Dispatch Queue/i })
    fireEvent.click(dispatchLink)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Dispatch Queue' })).toBeInTheDocument()
    })
  })

  it('10. Customer View link points correctly to /app and does not render fake admin link', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Saman Silva', role: 'ROLE_DISPATCHER', email: 'saman@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <AppRoutes />
      </MemoryRouter>
    )

    const customerLink = screen.getByRole('link', { name: /Customer View/i })
    expect(customerLink).toHaveAttribute('href', '/app')

    // Confirm no fake admin console route is present
    expect(screen.queryByRole('link', { name: /Admin Console/i })).not.toBeInTheDocument()
  })

  it('11. Preserves existing customer routes under /app', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Hiruni Customer', role: 'ROLE_CUSTOMER', email: 'hiruni@swiftdrop.lk' },
      loading: false,
      logout: vi.fn(),
    }

    render(
      <MemoryRouter initialEntries={['/app']}>
        <AppRoutes />
      </MemoryRouter>
    )

    // Customer sidebar should be present
    expect(screen.getByRole('link', { name: /Overview/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /My Shipments/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Create Delivery/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Delivery History/i })).toBeInTheDocument()

    // Dispatcher sidebar should NOT be rendered in CustomerLayout
    expect(screen.queryByRole('navigation', { name: 'Dispatch Operations' })).not.toBeInTheDocument()
  })
})
