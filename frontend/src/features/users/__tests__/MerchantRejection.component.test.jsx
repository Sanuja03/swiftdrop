import { beforeEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import UserManagement from '../pages/UserManagement'
import { getMerchantApplications, rejectMerchantApplication, approveMerchantApplication } from '../services/merchantApprovalApi'
import { getAllUsers } from '../services/userManagementApi'

vi.mock('../services/userManagementApi', () => ({ getAllUsers: vi.fn(async () => []), suspendUser: vi.fn(), restoreUser: vi.fn() }))
vi.mock('../services/merchantApprovalApi', () => ({ getMerchantApplications: vi.fn(), approveMerchantApplication: vi.fn(), rejectMerchantApplication: vi.fn() }))

beforeEach(() => {
  vi.clearAllMocks()
  getAllUsers.mockResolvedValue([])
  getMerchantApplications.mockResolvedValue([{ id: 'test-merchant', businessName: 'Test Business', registrationStatus: 'PENDING' }])
  rejectMerchantApplication.mockResolvedValue({})
  approveMerchantApplication.mockResolvedValue({})
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

it('allows rejecting an approved merchant without showing a duplicate approve action', async () => {
  getMerchantApplications.mockResolvedValue([{ id: 'approved-merchant', businessName: 'Approved Business', registrationStatus: 'APPROVED' }])
  await openRejection()
  expect(screen.queryByRole('button', { name: 'Approve', exact: true })).not.toBeInTheDocument()
  expect(screen.getByText(/merchant access will be blocked/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Rejection Reason (Required)'), { target: { value: 'Business details are inconsistent' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  await waitFor(() => expect(rejectMerchantApplication).toHaveBeenCalledWith('approved-merchant', 'Business details are inconsistent'))
})

it('allows approving a rejected application and refreshes account statuses too', async () => {
  getMerchantApplications.mockResolvedValue([{ id: 'rejected-merchant', businessName: 'Reviewed Business', registrationStatus: 'REJECTED' }])
  render(<MemoryRouter initialEntries={['/admin/users?tab=merchants']}><UserManagement /></MemoryRouter>)
  fireEvent.click(await screen.findByRole('button', { name: 'Approve', exact: true }))
  expect(screen.getByText(/from REJECTED to APPROVED/)).toBeInTheDocument()
  expect(screen.getByText(/Email verification is still required/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Reject', exact: true })).not.toBeInTheDocument()
  const previousReads = getAllUsers.mock.calls.length
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
  await waitFor(() => expect(approveMerchantApplication).toHaveBeenCalledWith('rejected-merchant', ''))
  await waitFor(() => expect(getAllUsers.mock.calls.length).toBeGreaterThan(previousReads))
})

it('shows submitted applications without listing accounts that have no application', async () => {
  getAllUsers.mockResolvedValue([
    { id: 'orphan-user', fullName: 'Legacy Merchant', role: 'ROLE_MERCHANT', status: 'PENDING_APPROVAL' },
    { id: 'linked-user', fullName: 'Linked Merchant', role: 'ROLE_MERCHANT', status: 'REJECTED' },
  ])
  getMerchantApplications.mockResolvedValue([
    { id: 'rejected-merchant', userId: 'linked-user', businessName: 'Linked Business', registrationStatus: 'REJECTED' },
  ])
  render(<MemoryRouter initialEntries={['/admin/users?tab=merchants']}><UserManagement /></MemoryRouter>)
  expect(await screen.findByText('Linked Business')).toBeInTheDocument()
  expect(screen.queryByRole('note', { name: 'Merchant accounts without applications' })).not.toBeInTheDocument()
  expect(screen.queryByText('Legacy Merchant')).not.toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: 'Approve', exact: true })).toHaveLength(1)
})

it('keeps merchant decisions separate and permits recovery of legacy suspended merchants', async () => {
  getMerchantApplications.mockResolvedValue([
    { id: 'application', userId: 'active-merchant', businessName: 'Approved Business', registrationStatus: 'APPROVED' },
    { id: 'legacy-application', userId: 'suspended-merchant', businessName: 'Legacy Business', registrationStatus: 'REJECTED' },
  ])
  getAllUsers.mockResolvedValue([
    { id: 'active-merchant', fullName: 'Approved Merchant', role: 'ROLE_MERCHANT', status: 'ACTIVE' },
    { id: 'suspended-merchant', fullName: 'Legacy Suspended Merchant', role: 'ROLE_MERCHANT', status: 'SUSPENDED' },
    { id: 'customer', fullName: 'Active Customer', role: 'ROLE_CUSTOMER', status: 'ACTIVE' },
    { id: 'courier', fullName: 'Suspended Courier', role: 'ROLE_COURIER', status: 'SUSPENDED' },
  ])
  render(<MemoryRouter><UserManagement /></MemoryRouter>)
  await screen.findByText('Approved Merchant')
  expect(screen.getAllByRole('button', { name: 'Suspend', exact: true })).toHaveLength(1)
  expect(screen.getAllByRole('button', { name: 'Restore', exact: true })).toHaveLength(2)
})

it('shows other users and only merchants with pending or approved applications in All Users', async () => {
  getAllUsers.mockResolvedValue([
    { id: 'legacy', fullName: 'Unregistered Salon', role: 'ROLE_MERCHANT', status: 'PENDING_APPROVAL' },
    { id: 'rejected', fullName: 'Rejected Merchant', role: 'ROLE_MERCHANT', status: 'REJECTED' },
    { id: 'pending', fullName: 'Pending Merchant', role: 'ROLE_MERCHANT', status: 'PENDING_APPROVAL' },
    { id: 'approved', fullName: 'Approved Merchant', role: 'ROLE_MERCHANT', status: 'ACTIVE' },
    { id: 'customer', fullName: 'Regular Customer', role: 'ROLE_CUSTOMER', status: 'ACTIVE' },
  ])
  getMerchantApplications.mockResolvedValue([
    { id: 'p', userId: 'pending', businessName: 'Pending Business', registrationStatus: 'PENDING' },
    { id: 'a', userId: 'approved', businessName: 'Approved Business', registrationStatus: 'APPROVED' },
    { id: 'r', userId: 'rejected', businessName: 'Rejected Business', registrationStatus: 'REJECTED' },
  ])
  render(<MemoryRouter><UserManagement /></MemoryRouter>)
  await screen.findByText('Pending Merchant')
  expect(screen.getByText('Approved Merchant')).toBeInTheDocument()
  expect(screen.getByText('Regular Customer')).toBeInTheDocument()
  expect(screen.queryByText('Unregistered Salon')).not.toBeInTheDocument()
  expect(screen.queryByText('Rejected Merchant')).not.toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'MERCHANT' } })
  expect(screen.getByText('Pending Merchant')).toBeInTheDocument()
  expect(screen.getByText('Approved Merchant')).toBeInTheDocument()
  expect(screen.queryByText('Regular Customer')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: /Merchant Approvals/ }))
  expect(await screen.findByText('Rejected Business')).toBeInTheDocument()
  expect(within(screen.getByText('Rejected Business').closest('tr')).getByRole('button', { name: 'Approve', exact: true })).toBeInTheDocument()
})
