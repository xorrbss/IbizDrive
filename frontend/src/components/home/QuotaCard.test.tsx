/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QuotaCard } from './QuotaCard'

vi.mock('@/hooks/useStorageQuota')
import { useStorageQuota } from '@/hooks/useStorageQuota'

describe('QuotaCard', () => {
  it('used / total + progress bar 표시', () => {
    vi.mocked(useStorageQuota).mockReturnValue({
      data: { usedBytes: 8_000_000_000, totalBytes: 50_000_000_000 },
      isLoading: false,
      isError: false,
    } as any)

    render(<QuotaCard />)
    const progress = screen.getByRole('progressbar')
    expect(progress.getAttribute('aria-valuenow')).toBe('16')
    expect(screen.getByText(/남음/)).toBeTruthy()
  })

  it('quota 0 — 할당량 미설정 표시', () => {
    vi.mocked(useStorageQuota).mockReturnValue({
      data: { usedBytes: 0, totalBytes: 0 },
      isLoading: false,
      isError: false,
    } as any)

    render(<QuotaCard />)
    expect(screen.getByText(/할당량 미설정/)).toBeTruthy()
  })

  it('80%+ amber tone', () => {
    vi.mocked(useStorageQuota).mockReturnValue({
      data: { usedBytes: 42_000_000_000, totalBytes: 50_000_000_000 },
      isLoading: false,
      isError: false,
    } as any)

    render(<QuotaCard />)
    const progress = screen.getByRole('progressbar')
    expect(progress.getAttribute('aria-valuenow')).toBe('84')
    const bar = progress.querySelector('div')
    expect(bar?.className).toContain('amber')
  })

  it('loading state', () => {
    vi.mocked(useStorageQuota).mockReturnValue({
      data: undefined, isLoading: true, isError: false,
    } as any)
    render(<QuotaCard />)
    expect(screen.getByText(/불러오는 중/)).toBeTruthy()
  })
})
