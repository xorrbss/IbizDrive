import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { StorageBar } from './StorageBar'

const getStorageQuotaMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: { getStorageQuota: () => getStorageQuotaMock() },
}))

function wrap(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('StorageBar', () => {
  beforeEach(() => {
    getStorageQuotaMock.mockReset()
  })

  it('65% 사용 중 표시', async () => {
    getStorageQuotaMock.mockResolvedValue({
      usedBytes: 65 * 1024 ** 3,
      totalBytes: 100 * 1024 ** 3,
    })
    wrap(<StorageBar />)
    await waitFor(() => {
      expect(screen.getByText('65%')).toBeTruthy()
    })
    const bar = screen.getByRole('progressbar')
    expect(bar.getAttribute('aria-valuenow')).toBe('65')
    expect(screen.getByText('65 GB / 100 GB')).toBeTruthy()
  })

  it('95% → warn 색상 적용', async () => {
    getStorageQuotaMock.mockResolvedValue({
      usedBytes: 95 * 1024 ** 3,
      totalBytes: 100 * 1024 ** 3,
    })
    wrap(<StorageBar />)
    await waitFor(() => {
      expect(screen.getByText('95%')).toBeTruthy()
    })
    const num = screen.getByText('95%')
    expect(num.className).toContain('text-warn')
  })

  it('로딩 중 placeholder 표시', () => {
    getStorageQuotaMock.mockImplementation(() => new Promise(() => {}))
    wrap(<StorageBar />)
    expect(screen.getByLabelText('저장공간 로딩 중')).toBeTruthy()
  })
})
