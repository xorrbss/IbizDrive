import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StorageBar } from './StorageBar'
import { useStorageQuota } from '@/hooks/useStorageQuota'

vi.mock('@/hooks/useStorageQuota', () => ({
  useStorageQuota: vi.fn(),
}))

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

beforeEach(() => {
  vi.mocked(useStorageQuota).mockReset()
})

describe('StorageBar (M15.3)', () => {
  it('로딩 중 — skeleton (aria-label "저장 용량 로딩")', () => {
    vi.mocked(useStorageQuota).mockReturnValue({
      data: undefined,
      isLoading: true,
    } as ReturnType<typeof useStorageQuota>)
    render(wrap(<StorageBar />))
    expect(screen.getByLabelText('저장 용량 로딩')).toBeTruthy()
  })

  it('데이터 — used/total + % 표시 + progressbar aria-valuenow', () => {
    vi.mocked(useStorageQuota).mockReturnValue({
      data: { usedBytes: 30 * 1024 * 1024 * 1024, totalBytes: 50 * 1024 * 1024 * 1024 },
      isLoading: false,
    } as ReturnType<typeof useStorageQuota>)
    const { container } = render(wrap(<StorageBar />))
    const bar = screen.getByRole('progressbar')
    expect(bar.getAttribute('aria-valuenow')).toBe('60')
    const text = container.textContent ?? ''
    expect(text).toMatch(/30(\.0)? GB/)
    expect(text).toMatch(/50(\.0)? GB/)
    expect(text).toContain('60%')
  })

  it('95%+ — danger 색 (bg-danger 클래스)', () => {
    vi.mocked(useStorageQuota).mockReturnValue({
      data: { usedBytes: 49 * 1024 * 1024 * 1024, totalBytes: 50 * 1024 * 1024 * 1024 },
      isLoading: false,
    } as ReturnType<typeof useStorageQuota>)
    const { container } = render(wrap(<StorageBar />))
    const fill = container.querySelector('.bg-danger')
    expect(fill).not.toBeNull()
  })
})
