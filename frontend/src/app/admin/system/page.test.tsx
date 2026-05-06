import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { CronJobsResponse } from '@/types/system'

/**
 * /admin/system — Wave 1 — T3 read-only cron 노출 페이지 검증.
 *
 * <p>핵심 가드:
 * <ul>
 *   <li>4 카드가 fixed order(purge → share → permission → storage)로 렌더</li>
 *   <li>각 카드: label / enabled 배지(ON/OFF) / cron / zone</li>
 *   <li>"read-only" 안내 문구가 페이지 헤더에 노출 (변경은 application.yml + 재기동)</li>
 *   <li>loading / error 분기</li>
 * </ul>
 *
 * <p>{@code useAdminSystemCron}은 mock — query/fetch 자체는 useAdminSystem.test 책임.
 */

const FIXTURE: CronJobsResponse = {
  jobs: [
    {
      key: 'purge.expired',
      label: '휴지통 hard purge',
      enabled: false,
      cron: '0 0 0 * * *',
      zone: 'Asia/Seoul',
      maxPerRun: 10000,
    },
    {
      key: 'share.expire',
      label: '공유 만료 처리',
      enabled: true,
      cron: '0 */5 * * * *',
      zone: 'Asia/Seoul',
      batchSize: 200,
    },
    {
      key: 'permission.expire',
      label: '권한 만료 처리',
      enabled: false,
      cron: '0 */5 * * * *',
      zone: 'Asia/Seoul',
      batchSize: 200,
    },
    {
      key: 'storage.orphan.cleanup',
      label: '스토리지 고아 정리',
      enabled: true,
      cron: '0 0 1 * * *',
      zone: 'Asia/Seoul',
      maxPerRun: 10000,
      graceHours: 24,
    },
  ],
}

const mockUseAdminSystemCron = vi.fn()
vi.mock('@/hooks/useAdminSystem', () => ({
  useAdminSystemCron: () => mockUseAdminSystemCron(),
}))

import AdminSystemPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('/admin/system — read-only cron status', () => {
  it('renders 4 jobs in fixed order with label/cron/zone/enabled badge', () => {
    mockUseAdminSystemCron.mockReturnValue({ data: FIXTURE, isLoading: false, isError: false })
    wrap(<AdminSystemPage />)

    // 헤더 + read-only 안내
    expect(screen.getByRole('heading', { level: 1, name: /시스템/ })).toBeTruthy()
    expect(screen.getByText(/application\.yml/)).toBeTruthy()

    // 4 카드 — testid로 순서 확인
    const cards = screen.getAllByTestId(/^cron-card-/)
    expect(cards).toHaveLength(4)
    expect(cards[0].getAttribute('data-testid')).toBe('cron-card-purge.expired')
    expect(cards[1].getAttribute('data-testid')).toBe('cron-card-share.expire')
    expect(cards[2].getAttribute('data-testid')).toBe('cron-card-permission.expire')
    expect(cards[3].getAttribute('data-testid')).toBe('cron-card-storage.orphan.cleanup')

    // 각 카드 페이로드
    expect(cards[0].textContent).toContain('휴지통 hard purge')
    expect(cards[0].textContent).toContain('OFF')
    expect(cards[0].textContent).toContain('0 0 0 * * *')
    expect(cards[0].textContent).toContain('Asia/Seoul')

    expect(cards[1].textContent).toContain('공유 만료 처리')
    expect(cards[1].textContent).toContain('ON')
    expect(cards[1].textContent).toContain('0 */5 * * * *')

    // batchSize / maxPerRun / graceHours 노출 확인 (옵셔널 필드)
    expect(cards[0].textContent).toContain('10000') // purge.maxPerRun
    expect(cards[1].textContent).toContain('200')   // share.batchSize
    expect(cards[3].textContent).toContain('24')    // storage.graceHours
  })

  it('shows loading state', () => {
    mockUseAdminSystemCron.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    wrap(<AdminSystemPage />)
    expect(screen.getByText(/불러오는 중/)).toBeTruthy()
  })

  it('shows error state', () => {
    mockUseAdminSystemCron.mockReturnValue({ data: undefined, isLoading: false, isError: true })
    wrap(<AdminSystemPage />)
    expect(screen.getByText(/불러오지 못했습니다/)).toBeTruthy()
  })
})
