import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AdminDashboardSummary } from '@/types/admin'

/**
 * /admin — KPI 대시보드 (admin-dashboard 트랙).
 *
 * <p>v1.x deferred landing 대체. 8개 KPI 그리드 + 로딩/에러 상태.
 * useAdminDashboardSummary mock으로 hook 격리, 컴포넌트 렌더만 검증.
 */
let dashboardState: {
  data?: AdminDashboardSummary
  isLoading: boolean
  isError: boolean
} = { isLoading: false, isError: false }

vi.mock('@/hooks/useAdminDashboardSummary', () => ({
  useAdminDashboardSummary: () => dashboardState,
}))

// AdminGuard 의존성 mock — /admin 페이지가 default `<AdminGuard>`로 감싸이게
// 변경되었으므로 (wave1.5-auditor-admin-ui-access) useMe + next/navigation을
// 격리. ADMIN role을 줘야 children이 렌더된다.
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))
vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({
    data: {
      user: { id: 'u1', email: 'a@b.com', name: 'A', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'k',
    },
    isLoading: false,
    isError: false,
  }),
}))

import AdminDashboardPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const SUMMARY: AdminDashboardSummary = {
  users: { total: 12, active: 10 },
  departments: { total: 4, active: 4 },
  folders: { active: 25 },
  files: { active: 117, trashed: 3 },
  audit: { last24h: 42 },
  storage: { usedBytes: 1610612736 }, // 1.5 GB
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    dashboardState = { isLoading: false, isError: false, data: SUMMARY }
  })

  it('성공 상태 — 8개 KPI 카드가 렌더된다', () => {
    wrap(<AdminDashboardPage />)
    // 카드 라벨로 존재 확인
    expect(screen.getByText('등록 사용자')).toBeTruthy()
    expect(screen.getByText('활성 사용자')).toBeTruthy()
    expect(screen.getByText('부서')).toBeTruthy()
    expect(screen.getByText('활성 폴더')).toBeTruthy()
    expect(screen.getByText('활성 파일')).toBeTruthy()
    expect(screen.getByText('휴지통 파일')).toBeTruthy()
    expect(screen.getByText('24시간 감사 이벤트')).toBeTruthy()
    expect(screen.getByText('스토리지 사용량')).toBeTruthy()
  })

  it('수치 — total/active/trashed/last24h가 KPI 카드에 표시', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.getByText('12')).toBeTruthy() // users.total
    expect(screen.getByText('10')).toBeTruthy() // users.active
    expect(screen.getByText('25')).toBeTruthy() // folders.active
    expect(screen.getByText('117')).toBeTruthy() // files.active
    expect(screen.getByText('3')).toBeTruthy() // files.trashed
    expect(screen.getByText('42')).toBeTruthy() // audit.last24h
    expect(screen.getByText('1.5 GB')).toBeTruthy() // storage formatted
  })

  it('로딩 상태 — "불러오는 중…" 표시', () => {
    dashboardState = { isLoading: true, isError: false }
    wrap(<AdminDashboardPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('에러 상태 — alert 메시지 노출', () => {
    dashboardState = { isLoading: false, isError: true }
    wrap(<AdminDashboardPage />)
    const alerts = screen.getAllByRole('alert')
    expect(
      alerts.some((a) => /불러오지 못했습니다|불러올 수 없습니다/.test(a.textContent ?? '')),
    ).toBe(true)
  })

  it('v1.x deferred 안내 박스가 더 이상 노출되지 않는다 (회귀 가드)', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.queryByText(/v1.x에서 추가 예정/)).toBeNull()
  })
})
