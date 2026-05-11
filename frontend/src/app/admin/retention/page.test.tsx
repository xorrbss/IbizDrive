import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import type { AdminTrashPolicy } from '@/types/admin-trash-policy'

/**
 * /admin/trash/policy — wave2-trash-policy-viewer.
 *
 * hook 모킹으로 loading/error/success UI 분기만 검증. fetch wire는 api 단위 테스트
 * (api.adminTrashPolicy.test) 책임. AdminGuard 격리는 storage page test 패턴 동형.
 */

let hookState: {
  data?: AdminTrashPolicy
  isLoading: boolean
  isError: boolean
} = { isLoading: true, isError: false }

vi.mock('@/hooks/useAdminTrashPolicy', () => ({
  useAdminTrashPolicy: () => hookState,
}))

// Phase C — mutation editor 의존성. 본 페이지 테스트는 editor 본체 행위는 별도
// (RetentionPolicyEditor.test.tsx)가 책임 — 여기서는 stub으로 page 통합만 검증.
vi.mock('@/components/admin/RetentionPolicyEditor', () => ({
  RetentionPolicyEditor: ({ currentDays }: { currentDays: number }) => (
    <div data-testid="retention-policy-editor-stub">
      editor for {currentDays}일
    </div>
  ),
}))

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

import AdminTrashPolicyPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('/admin/trash/policy', () => {
  beforeEach(() => {
    hookState = { isLoading: true, isError: false }
  })

  it('loading → 로딩 메시지', () => {
    hookState = { isLoading: true, isError: false }
    wrap(<AdminTrashPolicyPage />)
    expect(screen.getByText(/불러오는 중/)).toBeTruthy()
  })

  it('error → role=alert 메시지', () => {
    hookState = { isLoading: false, isError: true }
    wrap(<AdminTrashPolicyPage />)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('success → retentionDays 노출 + mutation editor 마운트 (Phase C)', () => {
    hookState = { isLoading: false, isError: false, data: { retentionDays: 30 } }
    wrap(<AdminTrashPolicyPage />)
    expect(screen.getByText('30')).toBeTruthy()
    expect(screen.getByTestId('retention-policy-editor-stub')).toBeTruthy()
    expect(screen.getByText(/editor for 30일/)).toBeTruthy()
  })

  it('loading/error 상태에서는 mutation editor 미마운트', () => {
    hookState = { isLoading: true, isError: false }
    wrap(<AdminTrashPolicyPage />)
    expect(screen.queryByTestId('retention-policy-editor-stub')).toBeNull()
  })

  it('non-default retention 값도 그대로 노출 (default 30 가정 회귀 가드)', () => {
    hookState = { isLoading: false, isError: false, data: { retentionDays: 14 } }
    wrap(<AdminTrashPolicyPage />)
    expect(screen.getByText('14')).toBeTruthy()
  })

  it('cron 운영 cross-link → /admin/system', () => {
    hookState = { isLoading: false, isError: false, data: { retentionDays: 30 } }
    wrap(<AdminTrashPolicyPage />)
    const link = screen.getByRole('link', { name: /admin\/system/ })
    expect(link.getAttribute('href')).toBe('/admin/system')
  })

  it('h1 페이지 제목 존재', () => {
    hookState = { isLoading: false, isError: false, data: { retentionDays: 30 } }
    wrap(<AdminTrashPolicyPage />)
    expect(screen.getByRole('heading', { level: 1, name: /휴지통 보존 정책/ })).toBeTruthy()
  })

  // trash-policy-dual-approval-callout — Phase B/C 활성화 후에도 2인 승인은 v1.x++ deferred
  // 명시. 운영자가 단일-approver 즉시 적용 사실을 인지하도록.
  it('2인 승인 deferred 안내 노출 — 단일-approver MVP 명시', () => {
    hookState = { isLoading: false, isError: false, data: { retentionDays: 30 } }
    wrap(<AdminTrashPolicyPage />)
    // 페이지 본문 "2인 승인" 섹션이 하나는 heading, 하나는 본문 → getAllByText
    const occurrences = screen.getAllByText(/2인 승인/)
    expect(occurrences.length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/dual-approval/)).toBeTruthy()
  })
})
