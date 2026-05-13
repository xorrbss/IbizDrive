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

const useAdminPendingApprovalsCountMock = vi.fn()
vi.mock('@/hooks/useAdminPendingApprovalsCount', () => ({
  useAdminPendingApprovalsCount: () => useAdminPendingApprovalsCountMock(),
}))

import { AdminTabBar } from './AdminTabBar'

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

describe('AdminTabBar — ADMIN 가시성 (T7-P1 T1.3)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
    useMeMock.mockReturnValue(session(['ADMIN']))
    useAdminPendingApprovalsCountMock.mockReset()
    useAdminPendingApprovalsCountMock.mockReturnValue({
      count: 0,
      isLoading: false,
      isError: false,
    })
  })

  it('ADMIN은 9탭 모두 노출 (overview/members/teams/permissions/storage/sharing/audit/retention/approvals)', () => {
    mockPathname.mockReturnValue('/admin')
    wrap(<AdminTabBar />)
    expect(screen.getByRole('link', { name: /개요/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /멤버/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /팀/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /폴더 권한/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /저장공간/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /공유 정책/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /감사 로그/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /보관/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /승인/ })).toBeTruthy()
  })

  it('approvals 활성 — /admin/approvals 접두사 (dual-approval Phase 4)', () => {
    mockPathname.mockReturnValue('/admin/approvals')
    wrap(<AdminTabBar />)
    expect(screen.getByRole('link', { name: /승인/ }).getAttribute('aria-current')).toBe('page')
  })

  it('overview 활성 — /admin 정확 일치 시 aria-current="page"', () => {
    mockPathname.mockReturnValue('/admin')
    wrap(<AdminTabBar />)
    const link = screen.getByRole('link', { name: /개요/ })
    expect(link.getAttribute('aria-current')).toBe('page')
  })

  it('overview는 /admin/users에서 활성 아님 (exact 매칭)', () => {
    mockPathname.mockReturnValue('/admin/users')
    wrap(<AdminTabBar />)
    const link = screen.getByRole('link', { name: /개요/ })
    expect(link.getAttribute('aria-current')).toBe(null)
  })

  it('members 활성 — /admin/users 접두사 (기존 라우트)', () => {
    mockPathname.mockReturnValue('/admin/users')
    wrap(<AdminTabBar />)
    expect(screen.getByRole('link', { name: /멤버/ }).getAttribute('aria-current')).toBe('page')
  })

  it('members 활성 — /admin/members 접두사 (Phase 2 rename 후 호환)', () => {
    mockPathname.mockReturnValue('/admin/members')
    wrap(<AdminTabBar />)
    expect(screen.getByRole('link', { name: /멤버/ }).getAttribute('aria-current')).toBe('page')
  })

  it('audit 활성 — /admin/audit 접두사', () => {
    mockPathname.mockReturnValue('/admin/audit/logs')
    wrap(<AdminTabBar />)
    expect(screen.getByRole('link', { name: /감사 로그/ }).getAttribute('aria-current')).toBe('page')
  })

  it('retention 활성 — /admin/trash/* 또는 /admin/retention', () => {
    mockPathname.mockReturnValue('/admin/trash/policy')
    wrap(<AdminTabBar />)
    expect(screen.getByRole('link', { name: /보관/ }).getAttribute('aria-current')).toBe('page')
  })
})

describe('AdminTabBar — AUDITOR 가시성 (wave1.5 답습)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
    mockPathname.mockReturnValue('/admin/audit/logs')
    useAdminPendingApprovalsCountMock.mockReset()
    useAdminPendingApprovalsCountMock.mockReturnValue({
      count: 0,
      isLoading: false,
      isError: false,
    })
  })

  it('AUDITOR는 audit 탭만 노출 (overview/members/teams/permissions/storage/sharing/retention/approvals 모두 hide)', () => {
    useMeMock.mockReturnValue(session(['AUDITOR']))
    wrap(<AdminTabBar />)
    expect(screen.getByRole('link', { name: /감사 로그/ })).toBeTruthy()
    expect(screen.queryByRole('link', { name: /개요/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /멤버/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /팀/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /폴더 권한/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /저장공간/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /공유 정책/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /보관/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /승인/ })).toBeNull()
  })

  it('MEMBER (방어) → 빈 nav', () => {
    useMeMock.mockReturnValue(session(['MEMBER']))
    wrap(<AdminTabBar />)
    expect(screen.queryByRole('link', { name: /감사 로그/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /개요/ })).toBeNull()
  })

  it('useMe loading (data undefined) → 빈 nav', () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    wrap(<AdminTabBar />)
    expect(screen.queryByRole('link', { name: /개요/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /감사 로그/ })).toBeNull()
  })
})

describe('AdminTabBar — approvals pending count 배지 (dual-approval Phase 4 follow-up)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
    useMeMock.mockReturnValue(session(['ADMIN']))
    useAdminPendingApprovalsCountMock.mockReset()
    mockPathname.mockReturnValue('/admin')
  })

  it('count=3 → 승인 탭 배지 표시 + aria-label/숫자 정합', () => {
    useAdminPendingApprovalsCountMock.mockReturnValue({
      count: 3,
      isLoading: false,
      isError: false,
    })
    wrap(<AdminTabBar />)
    const badge = screen.getByTestId('admin-tab-badge-approvals')
    expect(badge.textContent).toBe('3')
    expect(badge.getAttribute('aria-label')).toBe('검토 대기 3건')
  })

  it('count=0 → 배지 미렌더', () => {
    useAdminPendingApprovalsCountMock.mockReturnValue({
      count: 0,
      isLoading: false,
      isError: false,
    })
    wrap(<AdminTabBar />)
    expect(screen.queryByTestId('admin-tab-badge-approvals')).toBeNull()
  })

  it('AUDITOR + count=0 (hook disabled) → 배지 미렌더 (승인 탭 자체도 미노출)', () => {
    useMeMock.mockReturnValue(session(['AUDITOR']))
    useAdminPendingApprovalsCountMock.mockReturnValue({
      count: 0,
      isLoading: false,
      isError: false,
    })
    mockPathname.mockReturnValue('/admin/audit')
    wrap(<AdminTabBar />)
    expect(screen.queryByRole('link', { name: /승인/ })).toBeNull()
    expect(screen.queryByTestId('admin-tab-badge-approvals')).toBeNull()
  })

  it('sharing 배지와 approvals 배지가 동시에 렌더 (서로 독립)', () => {
    useAdminPendingApprovalsCountMock.mockReturnValue({
      count: 5,
      isLoading: false,
      isError: false,
    })
    wrap(<AdminTabBar />)
    const sharingBadge = screen.queryByTestId('admin-tab-badge-sharing')
    const approvalsBadge = screen.getByTestId('admin-tab-badge-approvals')
    // sharing mock(ADMIN_FLAGGED.length)이 0보다 크면 두 배지가 동시에 노출
    if (sharingBadge) {
      expect(sharingBadge.textContent).not.toBe('0')
    }
    expect(approvalsBadge.textContent).toBe('5')
  })
})
