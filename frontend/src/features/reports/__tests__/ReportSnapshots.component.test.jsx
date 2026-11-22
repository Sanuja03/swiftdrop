import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import ReportSnapshots from '../pages/ReportSnapshots'
import { getReportSnapshots } from '../services/reportApi'

vi.mock('../services/reportApi', () => ({
  REPORT_SNAPSHOT_PAGE_SIZE: 5,
  getReportSnapshots: vi.fn(), getReportSnapshot: vi.fn(), saveReportSnapshot: vi.fn(),
}))

it('pages seven saved reports in groups of five and resets pagination when filtering', async () => {
  const items = Array.from({ length: 7 }, (_, index) => ({
    id: `report-${index}`, createdAt: '2026-11-18T03:00:00Z',
    startDate: '2026-11-12', endDate: '2026-11-18',
  }))
  getReportSnapshots.mockImplementation(async (page) => ({
    items: items.slice(page * 5, page * 5 + 5), total: 7, size: 5,
  }))
  render(<ReportSnapshots canSave={false} onOpen={vi.fn()} />)
  expect(await screen.findByText('Showing 1–5 of 7 saved reports')).toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: 'Open report' })).toHaveLength(5)
  expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Next' }))
  expect(await screen.findByText('Showing 6–7 of 7 saved reports')).toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: 'Open report' })).toHaveLength(2)
  expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Previous' }))
  expect(await screen.findByText('Showing 1–5 of 7 saved reports')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Next' }))
  await screen.findByText('Showing 6–7 of 7 saved reports')
  fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } })
  expect(await screen.findByText('Showing 1–5 of 7 saved reports')).toBeInTheDocument()
  await waitFor(() => expect(getReportSnapshots).toHaveBeenLastCalledWith(
    0, expect.any(Object), expect.objectContaining({ startDate: expect.any(String), endDate: expect.any(String) }),
  ))
})
