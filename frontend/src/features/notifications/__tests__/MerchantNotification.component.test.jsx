import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MyNotifications from '../pages/MyNotifications'
import UserManagement from '@/features/users/pages/UserManagement'
import { getMyNotifications, markNotificationAsRead } from '../services/notificationApi'

let role = 'ROLE_ADMIN'
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ user: { role } }) }))
vi.mock('../services/notificationApi', () => ({ getMyNotifications: vi.fn(), markNotificationAsRead: vi.fn() }))
vi.mock('@/features/users/services/userManagementApi', () => ({ getAllUsers: vi.fn(async () => []), suspendUser: vi.fn(), restoreUser: vi.fn() }))
vi.mock('@/features/users/services/merchantApprovalApi', () => ({ getMerchantApplications: vi.fn(async () => []), approveMerchantApplication: vi.fn(), rejectMerchantApplication: vi.fn() }))

const alert = {
  id: 'test-alert', title: 'Merchant Approval Pending', message: 'Test Shop has submitted a merchant application.',
  notificationType: 'MERCHANT_APPROVAL', isRead: false, createdAt: '2026-11-19T00:00:00Z',
}

function view() {
  render(<MemoryRouter initialEntries={['/notifications']}><Routes>
    <Route path="/notifications" element={<MyNotifications />} />
    <Route path="/admin/users" element={<UserManagement />} />
  </Routes></MemoryRouter>)
}

describe('Merchant approval notification review', () => {
  beforeEach(() => {
    role = 'ROLE_ADMIN'
    vi.clearAllMocks()
    getMyNotifications.mockResolvedValue([alert])
    markNotificationAsRead.mockResolvedValue({ ...alert, isRead: true })
  })

  for (const operationsRole of ['ROLE_ADMIN', 'ROLE_DISPATCHER']) {
    it(`${operationsRole} can open the Merchant Approvals tab directly from its alert`, async () => {
      role = operationsRole
      view()
      fireEvent.click(await screen.findByRole('link', { name: 'Review merchant applications' }))
      expect(await screen.findByRole('heading', { name: 'Merchant Applications' })).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Registered Users' })).not.toBeInTheDocument()
    })
  }

  it('read action clears the unread filter and keeps the saved alert in All', async () => {
    view()
    fireEvent.click(await screen.findByRole('button', { name: 'Mark as read' }))
    await waitFor(() => expect(markNotificationAsRead).toHaveBeenCalledWith('test-alert'))
    fireEvent.click(await screen.findByRole('button', { name: 'Unread (0)' }))
    expect(await screen.findByText("You're all caught up!")).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'All (1)' }))
    expect(screen.getByText('Merchant Approval Pending')).toBeInTheDocument()
  })

  it('customers retain parcel wording and receive no operations action link', async () => {
    role = 'ROLE_CUSTOMER'
    getMyNotifications.mockResolvedValue([])
    view()
    expect(await screen.findByText('Your parcel and account updates will appear here.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Review merchant applications' })).not.toBeInTheDocument()
  })
})
