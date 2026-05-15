import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { CronJobsResponse } from '@/types/system'
import type { AuthSession } from '@/types/auth'

/**
 * /admin/system — Wave 1 — T3 read-only cron 노출 페이지 + admin-cron-policy-toggle 확장.
 *
 * <p>핵심 가드:
 * <ul>
 *   <li>6 카드가 fixed order(purge → share → permission → storage → admin.approval → favorites.cleanup)로 렌더</li>
 *   <li>각 카드: label / enabled 배지(ON/OFF) / cron / zone</li>
 *   <li>loading / error 분기</li>
 *   <li>(P6) ADMIN 세션 — 6 카드 모두 토글 switch 노출</li>
 *   <li>(P6) AUDITOR 세션 — 토글 switch 미노출, 카드는 그대로</li>
 *   <li>(P6) 토글 클릭 → ConfirmDialog → 확인 → mutation 호출</li>
 *   <li>(P6) 토글 클릭 → ConfirmDialog → 취소 → mutation 미호출</li>
 * </ul>
 *
 * <p>{@code useAdminSystemCron}/{@code useAdminToggleCron}/{@code useMe}는 mock —
 * query/mutation/auth fetch 자체는 각자의 hook 단위 테스트 책임.
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
    {
      key: 'admin.approval.expire',
      label: '2인 승인 만료 처리',
      enabled: false,
      cron: '0 */5 * * * *',
      zone: 'Asia/Seoul',
      batchSize: 200,
    },
    {
      key: 'favorites.cleanup',
      label: '즐겨찾기 orphan 정리',
      enabled: false,
      cron: '0 0 2 * * *',
      zone: 'Asia/Seoul',
    },
  ],
}

const SESSION_ADMIN: AuthSession = {
  user: { id: 'u1', email: 'admin@example.com', name: 'Admin', kind: 'human', mustChangePassword: false },
  departments: [],
  roles: ['ADMIN'],
  effectivePermissionsCacheKey: 'k',
}

const SESSION_AUDITOR: AuthSession = {
  user: { id: 'u2', email: 'auditor@example.com', name: 'Auditor', kind: 'human', mustChangePassword: false },
  departments: [],
  roles: ['AUDITOR'],
  effectivePermissionsCacheKey: 'k',
}

const mockUseAdminSystemCron = vi.fn()
const mockUseAdminToggleCron = vi.fn()
const mockUseMe = vi.fn()

vi.mock('@/hooks/useAdminSystem', () => ({
  useAdminSystemCron: () => mockUseAdminSystemCron(),
  useAdminToggleCron: () => mockUseAdminToggleCron(),
}))
vi.mock('@/hooks/useMe', () => ({
  useMe: () => mockUseMe(),
}))

import AdminSystemPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('/admin/system — read-only cron status', () => {
  beforeEach(() => {
    mockUseAdminSystemCron.mockReset()
    mockUseAdminToggleCron.mockReset()
    mockUseMe.mockReset()
    // 기본값: AUDITOR(토글 비노출) + idle mutation. 토글 케이스에서 override.
    mockUseAdminToggleCron.mockReturnValue({ mutate: vi.fn(), isPending: false })
    mockUseMe.mockReturnValue({ data: SESSION_AUDITOR })
  })

  it('renders 6 jobs in fixed order with label/cron/zone/enabled badge', () => {
    mockUseAdminSystemCron.mockReturnValue({ data: FIXTURE, isLoading: false, isError: false })
    wrap(<AdminSystemPage />)

    // 헤더
    expect(screen.getByRole('heading', { level: 1, name: /시스템/ })).toBeTruthy()

    // 6 카드 — testid로 순서 확인
    const cards = screen.getAllByTestId(/^cron-card-/)
    expect(cards).toHaveLength(6)
    expect(cards[0].getAttribute('data-testid')).toBe('cron-card-purge.expired')
    expect(cards[1].getAttribute('data-testid')).toBe('cron-card-share.expire')
    expect(cards[2].getAttribute('data-testid')).toBe('cron-card-permission.expire')
    expect(cards[3].getAttribute('data-testid')).toBe('cron-card-storage.orphan.cleanup')
    expect(cards[4].getAttribute('data-testid')).toBe('cron-card-admin.approval.expire')
    expect(cards[5].getAttribute('data-testid')).toBe('cron-card-favorites.cleanup')

    // 각 카드 페이로드
    expect(cards[0].textContent).toContain('휴지통 hard purge')
    expect(cards[0].textContent).toContain('OFF')
    expect(cards[0].textContent).toContain('0 0 0 * * *')
    expect(cards[0].textContent).toContain('Asia/Seoul')

    expect(cards[1].textContent).toContain('공유 만료 처리')
    expect(cards[1].textContent).toContain('ON')
    expect(cards[1].textContent).toContain('0 */5 * * * *')

    // 5/6번째 카드 (V21/V23 drift recovery): label + cron + OFF 배지
    expect(cards[4].textContent).toContain('2인 승인 만료 처리')
    expect(cards[4].textContent).toContain('OFF')
    expect(cards[5].textContent).toContain('즐겨찾기 orphan 정리')
    expect(cards[5].textContent).toContain('OFF')
    expect(cards[5].textContent).toContain('0 0 2 * * *')

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

describe('AdminSystemPage — cron 토글 (admin-cron-policy-toggle)', () => {
  beforeEach(() => {
    mockUseAdminSystemCron.mockReset()
    mockUseAdminToggleCron.mockReset()
    mockUseMe.mockReset()
    mockUseAdminSystemCron.mockReturnValue({ data: FIXTURE, isLoading: false, isError: false })
    mockUseAdminToggleCron.mockReturnValue({ mutate: vi.fn(), isPending: false })
  })

  it('ADMIN 세션 — 6 카드 모두에 토글 switch 노출', async () => {
    mockUseMe.mockReturnValue({ data: SESSION_ADMIN })
    wrap(<AdminSystemPage />)
    await waitFor(() => {
      expect(screen.getByTestId('cron-card-purge.expired')).toBeTruthy()
    })
    expect(screen.getByTestId('cron-toggle-purge.expired')).toBeTruthy()
    expect(screen.getByTestId('cron-toggle-share.expire')).toBeTruthy()
    expect(screen.getByTestId('cron-toggle-permission.expire')).toBeTruthy()
    expect(screen.getByTestId('cron-toggle-storage.orphan.cleanup')).toBeTruthy()
    expect(screen.getByTestId('cron-toggle-admin.approval.expire')).toBeTruthy()
    expect(screen.getByTestId('cron-toggle-favorites.cleanup')).toBeTruthy()
  })

  it('AUDITOR 세션 — 토글 switch 미노출, 카드는 그대로', async () => {
    mockUseMe.mockReturnValue({ data: SESSION_AUDITOR })
    wrap(<AdminSystemPage />)
    await waitFor(() => {
      expect(screen.getByTestId('cron-card-purge.expired')).toBeTruthy()
    })
    expect(screen.queryByTestId('cron-toggle-purge.expired')).toBeNull()
    expect(screen.queryByTestId('cron-toggle-share.expire')).toBeNull()
    expect(screen.queryByTestId('cron-toggle-permission.expire')).toBeNull()
    expect(screen.queryByTestId('cron-toggle-storage.orphan.cleanup')).toBeNull()
    expect(screen.queryByTestId('cron-toggle-admin.approval.expire')).toBeNull()
    expect(screen.queryByTestId('cron-toggle-favorites.cleanup')).toBeNull()
  })

  it('토글 클릭 → ConfirmDialog → 확인 → mutation 호출', async () => {
    const mutate = vi.fn()
    mockUseAdminToggleCron.mockReturnValue({ mutate, isPending: false })
    mockUseMe.mockReturnValue({ data: SESSION_ADMIN })
    wrap(<AdminSystemPage />)
    const toggle = await screen.findByTestId('cron-toggle-purge.expired')
    // purge.expired 는 fixture에서 enabled=false → 토글 시 requested=true(활성화)
    fireEvent.click(toggle)
    expect(screen.getByTestId('cron-confirm-dialog')).toBeTruthy()
    fireEvent.click(screen.getByTestId('cron-confirm-confirm'))
    await waitFor(() => {
      expect(mutate).toHaveBeenCalledTimes(1)
    })
    expect(mutate).toHaveBeenCalledWith(
      { key: 'purge.expired', enabled: true },
      expect.objectContaining({ onSettled: expect.any(Function) }),
    )
  })

  it('ConfirmDialog 취소 → mutation 미호출', async () => {
    const mutate = vi.fn()
    mockUseAdminToggleCron.mockReturnValue({ mutate, isPending: false })
    mockUseMe.mockReturnValue({ data: SESSION_ADMIN })
    wrap(<AdminSystemPage />)
    const toggle = await screen.findByTestId('cron-toggle-purge.expired')
    fireEvent.click(toggle)
    expect(screen.getByTestId('cron-confirm-dialog')).toBeTruthy()
    fireEvent.click(screen.getByTestId('cron-confirm-cancel'))
    expect(mutate).not.toHaveBeenCalled()
    // dialog 닫힘
    expect(screen.queryByTestId('cron-confirm-dialog')).toBeNull()
  })
})
