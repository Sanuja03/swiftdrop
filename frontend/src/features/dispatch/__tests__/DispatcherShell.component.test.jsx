import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import DispatcherRoute from '../components/DispatcherRoute'
import DispatcherLayout from '../layouts/DispatcherLayout'

// Mock AuthContext
const mockLogout = vi.fn()
let mockAuthState = {
  isAuthenticated: true,
  user: { fullName: 'Bandara Silva', role: 'ROLE_DISPATCHER', email: 'bandara@swiftdrop.lk' },
  loading: false,
  logout: mockLogout,
}

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => mockAuthState,
}))

describe('Dispatcher Role Guard & Layout Shell Tests', () => {
  it('1. Allows ROLE_DISPATCHER to view protected dispatch routes', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Kamal Perera', role: 'ROLE_DISPATCHER' },
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route
            path="/dispatch"
            element={
              <DispatcherRoute>
                <div>Dispatcher Content Active</div>
              </DispatcherRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Dispatcher Content Active')).toBeInTheDocument()
  })

  it('2. Allows ROLE_ADMIN to view protected dispatch routes', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Thenuri Admin', role: 'ROLE_ADMIN' },
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route
            path="/dispatch"
            element={
              <DispatcherRoute>
                <div>Admin In Dispatch Queue</div>
              </DispatcherRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Admin In Dispatch Queue')).toBeInTheDocument()
  })

  it('3. Denies ROLE_CUSTOMER with clear 403 Access Denied screen', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Hiruni Customer', role: 'ROLE_CUSTOMER' },
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route
            path="/dispatch"
            element={
              <DispatcherRoute>
                <div>Secret Dispatch Queue</div>
              </DispatcherRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.queryByText('Secret Dispatch Queue')).not.toBeInTheDocument()
    expect(screen.getByText('403 - Access Denied')).toBeInTheDocument()
    expect(screen.getByText(/Dispatcher access required/)).toBeInTheDocument()
    expect(screen.getByText('CUSTOMER')).toBeInTheDocument()
    expect(screen.getByText('Back to Customer Portal')).toBeInTheDocument()
  })

  it('4. Denies ROLE_COURIER with clear 403 Access Denied screen', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Sunil Courier', role: 'ROLE_COURIER' },
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route
            path="/dispatch"
            element={
              <DispatcherRoute>
                <div>Secret Dispatch Queue</div>
              </DispatcherRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.queryByText('Secret Dispatch Queue')).not.toBeInTheDocument()
    expect(screen.getByText('403 - Access Denied')).toBeInTheDocument()
    expect(screen.getByText('COURIER')).toBeInTheDocument()
  })

  it('5. Denies ROLE_MERCHANT with clear 403 Access Denied screen', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Store Owner', role: 'ROLE_MERCHANT' },
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route
            path="/dispatch"
            element={
              <DispatcherRoute>
                <div>Secret Dispatch Queue</div>
              </DispatcherRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('403 - Access Denied')).toBeInTheDocument()
    expect(screen.getByText('MERCHANT')).toBeInTheDocument()
  })

  it('6. Redirects unauthenticated visitor to /login', () => {
    mockAuthState = {
      isAuthenticated: false,
      user: null,
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route
            path="/dispatch"
            element={
              <DispatcherRoute>
                <div>Secret Dispatch Queue</div>
              </DispatcherRoute>
            }
          />
          <Route path="/login" element={<div>Login Page Target</div>} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.queryByText('Secret Dispatch Queue')).not.toBeInTheDocument()
    expect(screen.getByText('Login Page Target')).toBeInTheDocument()
  })

  it('7. DispatcherLayout renders sidebar navigation and topbar identity', () => {
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Bandara Silva', role: 'ROLE_DISPATCHER' },
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route path="/dispatch" element={<DispatcherLayout />}>
            <Route index element={<div>Active Queue Page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    )

    // Sidebar branding and links
    expect(screen.getByText('Operations Portal')).toBeInTheDocument()
    expect(screen.getByText('Dispatch Queue')).toBeInTheDocument()
    expect(screen.getByText('Courier Workloads')).toBeInTheDocument()
    expect(screen.getByText('Customer View')).toBeInTheDocument()

    // Topbar identity
    expect(screen.getByText('Dispatch Operations')).toBeInTheDocument()
    expect(screen.getByText('Bandara Silva')).toBeInTheDocument()
    expect(screen.getByText('DISPATCHER')).toBeInTheDocument()
    expect(screen.getByText('Active Queue Page')).toBeInTheDocument()
  })

  it('8. DispatcherTopbar calls logout when Sign Out is clicked', () => {
    mockLogout.mockClear()
    mockAuthState = {
      isAuthenticated: true,
      user: { fullName: 'Bandara Silva', role: 'ROLE_DISPATCHER' },
      loading: false,
      logout: mockLogout,
    }

    render(
      <MemoryRouter initialEntries={['/dispatch']}>
        <Routes>
          <Route path="/dispatch" element={<DispatcherLayout />}>
            <Route index element={<div>Dummy View</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    )

    const signOutBtn = screen.getByTitle('Sign Out of SwiftDrop')
    fireEvent.click(signOutBtn)
    expect(mockLogout).toHaveBeenCalledTimes(1)
  })
})
