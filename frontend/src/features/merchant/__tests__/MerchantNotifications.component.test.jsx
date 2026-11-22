import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import MerchantPortal from '../pages/MerchantPortal'

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ user: { role: 'ROLE_MERCHANT' } }) }))
vi.mock('../services/merchantApi', () => ({ merchantApi: {
  getProfile: vi.fn(async () => ({ id: 'test-merchant', businessName: 'Test Business', registrationStatus: 'APPROVED' })),
  getWebhookConfig: vi.fn(async () => ({ configured: false })),
  getStats: vi.fn(async () => ({})), getOrders: vi.fn(async () => []), getWebhookLogs: vi.fn(async () => []),
  calculateFee: vi.fn(async () => ({ totalFee: 0 })),
} }))
vi.mock('@/features/notifications/services/notificationApi', () => ({
  getMyNotifications: vi.fn(async () => [{ id: 'approval', title: 'Merchant Application Approved',
    message: 'Your application has been approved.', notificationType: 'MERCHANT_APPROVED', isRead: false }]),
  markNotificationAsRead: vi.fn(),
}))

it('lets an approved merchant open the decision notification from its own portal', async () => {
  render(<MemoryRouter><MerchantPortal /></MemoryRouter>)
  fireEvent.click(await screen.findByRole('button', { name: /Notifications/ }))
  expect(await screen.findByText('Merchant Application Approved')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Mark as read' })).toBeInTheDocument()
})
