import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const mockPathname = vi.fn()
vi.mock('next/navigation', () => ({
  usePathname: () => mockPathname(),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

import { AdminSideNav } from './AdminSideNav'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const session = (roles: string[]) => ({
  data: {
    user: {
      id: 'u1',
      email: 'a@b.com',
      name: 'Alice',
      kind: 'human' as const,
      mustChangePassword: false,
    },
    departments: [],
    roles,
    effectivePermissionsCacheKey: 'k',
  },
  isLoading: false,
  isError: false,
})

describe('AdminSideNav (admin-dashboard 트랙 갱신, ADMIN 활성)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
    useMeMock.mockReturnValue(session(['ADMIN']))
  })

  it('대시보드는 활성 링크 — /admin 정확 일치 시 aria-current="page"', () => {
    mockPathname.mockReturnValue('/admin')
    wrap(<AdminSideNav />)
    const link = screen.getByRole('link', { name: '대시보드' })
    expect(link.getAttribute('href')).toBe('/admin')
    expect(link.getAttribute('aria-current')).toBe('page')
  })

  it('대시보드는 /admin/users에서는 활성 아님 (exact 매칭 — prefix 오작동 가드)', () => {
    mockPathname.mockReturnValue('/admin/users')
    wrap(<AdminSideNav />)
    const link = screen.getByRole('link', { name: '대시보드' })
    expect(link.getAttribute('aria-current')).toBe(null)
  })

  it('"v1.x 예정" 영역에 대시보드가 더 이상 없다 (회귀 가드)', () => {
    mockPathname.mockReturnValue('/admin')
    wrap(<AdminSideNav />)
    // 활성 링크는 통과하지만 disabled span으로는 노출되면 안 됨 — 활성 링크는 1개만 존재해야 함.
    const allDashboard = screen.getAllByText('대시보드')
    expect(allDashboard).toHaveLength(1)
    const span = allDashboard[0].closest('[aria-disabled="true"]')
    expect(span).toBeNull()
  })
})

describe('AdminSideNav — role 분기 (wave1.5-auditor-admin-ui-access)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
    mockPathname.mockReturnValue('/admin')
  })

  it('roles=[ADMIN] → 7개 활성 항목 모두 노출', () => {
    useMeMock.mockReturnValue(session(['ADMIN']))
    wrap(<AdminSideNav />)
    expect(screen.getByRole('link', { name: '대시보드' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '감사 로그' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '사용자 초대' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '부서' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '권한' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '시스템' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '스토리지' })).toBeTruthy()
  })

  it('roles=[AUDITOR] → 감사 로그 + 시스템만 노출, 나머지 hide', () => {
    useMeMock.mockReturnValue(session(['AUDITOR']))
    wrap(<AdminSideNav />)
    expect(screen.getByRole('link', { name: '감사 로그' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '시스템' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: '대시보드' })).toBeNull()
    expect(screen.queryByRole('link', { name: '사용자 초대' })).toBeNull()
    expect(screen.queryByRole('link', { name: '부서' })).toBeNull()
    expect(screen.queryByRole('link', { name: '권한' })).toBeNull()
    expect(screen.queryByRole('link', { name: '스토리지' })).toBeNull()
  })

  it('roles=[AUDITOR] → "예정" deferred 섹션도 hide (AUDITOR 화면 단순화)', () => {
    useMeMock.mockReturnValue(session(['AUDITOR']))
    wrap(<AdminSideNav />)
    expect(screen.queryByText('예정')).toBeNull()
    expect(screen.queryByText('휴지통')).toBeNull()
    expect(screen.queryByText('Legal Hold')).toBeNull()
  })

  it('roles=[ADMIN] → "예정" deferred 섹션 노출 (회귀)', () => {
    useMeMock.mockReturnValue(session(['ADMIN']))
    wrap(<AdminSideNav />)
    expect(screen.getByText('예정')).toBeTruthy()
    expect(screen.getByText('휴지통')).toBeTruthy()
  })

  it('roles=[MEMBER] → 활성 항목 모두 hide (방어 — 정상 경로에서는 layout이 차단)', () => {
    useMeMock.mockReturnValue(session(['MEMBER']))
    wrap(<AdminSideNav />)
    expect(screen.queryByRole('link', { name: '대시보드' })).toBeNull()
    expect(screen.queryByRole('link', { name: '감사 로그' })).toBeNull()
    expect(screen.queryByRole('link', { name: '시스템' })).toBeNull()
  })

  it('useMe loading (data undefined) → 빈 nav (방어)', () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    wrap(<AdminSideNav />)
    expect(screen.queryByRole('link', { name: '대시보드' })).toBeNull()
    expect(screen.queryByRole('link', { name: '감사 로그' })).toBeNull()
  })
})
