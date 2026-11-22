import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import NotificationBell from './NotificationBell'
import { getUnreadCount } from '../services/notificationApi'
vi.mock('../services/notificationApi', () => ({ getUnreadCount: vi.fn() }))
beforeEach(() => vi.clearAllMocks())
it('shows unread count and refreshes after a notification is read', async () => {
  getUnreadCount.mockResolvedValueOnce(3).mockResolvedValue(0)
  render(<MemoryRouter><NotificationBell /></MemoryRouter>)
  expect(await screen.findByRole('link', { name: 'Notifications, 3 unread' })).toHaveAttribute('href', '/notifications')
  fireEvent(window, new Event('swiftdrop:notifications-changed'))
  await waitFor(() => expect(screen.getByRole('link', { name: 'Notifications', exact: true })).toBeInTheDocument())
  expect(screen.queryByText('3')).not.toBeInTheDocument()
})
it('keeps notifications accessible if the unread count fails', async () => {
  getUnreadCount.mockRejectedValue(new Error('Offline'))
  render(<MemoryRouter><NotificationBell /></MemoryRouter>)
  await waitFor(() => expect(getUnreadCount).toHaveBeenCalled())
  expect(screen.getByRole('link', { name: 'Notifications' })).toHaveAttribute('href', '/notifications')
})
