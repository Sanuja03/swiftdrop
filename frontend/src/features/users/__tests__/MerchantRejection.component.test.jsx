import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import UserManagement from '../pages/UserManagement'
import { getMerchantApplications, rejectMerchantApplication } from '../services/merchantApprovalApi'

vi.mock('../services/userManagementApi', () => ({ getAllUsers: vi.fn(async () => []), suspendUser: vi.fn(), restoreUser: vi.fn() }))
vi.mock('../services/merchantApprovalApi', () => ({ getMerchantApplications: vi.fn(), approveMerchantApplication: vi.fn(), rejectMerchantApplication: vi.fn() }))

beforeEach(() => {
  vi.clearAllMocks()
  getMerchantApplications.mockResolvedValue([{ id: 'test-merchant', businessName: 'Test Business', registrationStatus: 'PENDING' }])
  rejectMerchantApplication.mockResolvedValue({})
})

async function openRejection() {
  render(<MemoryRouter initialEntries={['/admin/users?tab=merchants']}><UserManagement /></MemoryRouter>)
  fireEvent.click(await screen.findByRole('button', { name: 'Reject', exact: true }))
}

it('requires a selection and sends the selected reason with optional details', async () => {
  await openRejection()
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Select a rejection reason.')
  expect(rejectMerchantApplication).not.toHaveBeenCalled()
  fireEvent.change(screen.getByLabelText('Rejection Reason (Required)'), { target: { value: 'Business details are inconsistent' } })
  fireEvent.change(screen.getByLabelText('Additional Details (Optional)'), { target: { value: '  The business address differs from the contact details.  ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  await waitFor(() => expect(rejectMerchantApplication).toHaveBeenCalledWith('test-merchant',
    'Business details are inconsistent\n\nDetails: The business address differs from the contact details.'))
})

it('requires a custom explanation for Other', async () => {
  await openRejection()
  fireEvent.change(screen.getByLabelText('Rejection Reason (Required)'), { target: { value: 'Other' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Explain the rejection reason when selecting Other.')
  expect(rejectMerchantApplication).not.toHaveBeenCalled()
  fireEvent.change(screen.getByLabelText('Explain the Reason (Required)'), { target: { value: '  Please clarify the business ownership details.  ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  await waitFor(() => expect(rejectMerchantApplication).toHaveBeenCalledWith('test-merchant', 'Please clarify the business ownership details.'))
})

it('sends a preset reason without requiring additional text', async () => {
  await openRejection()
  fireEvent.change(screen.getByLabelText('Rejection Reason (Required)'), { target: { value: 'Duplicate merchant application' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  await waitFor(() => expect(rejectMerchantApplication).toHaveBeenCalledWith('test-merchant', 'Duplicate merchant application'))
})

it('allows rejecting an approved merchant while keeping approval pending-only', async () => {
  getMerchantApplications.mockResolvedValue([{ id: 'approved-merchant', businessName: 'Approved Business', registrationStatus: 'APPROVED' }])
  await openRejection()
  expect(screen.queryByRole('button', { name: 'Approve', exact: true })).not.toBeInTheDocument()
  expect(screen.getByText(/merchant access will be blocked/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Rejection Reason (Required)'), { target: { value: 'Business details are inconsistent' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  await waitFor(() => expect(rejectMerchantApplication).toHaveBeenCalledWith('approved-merchant', 'Business details are inconsistent'))
})
