import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import type { AdminStorageOverviewResponse } from '@/types/admin-storage'

/**
 * /admin/storage — overview hook 모킹 후 UI 매트릭스 (loading/error/success).
 *
 * <p>fetch wire는 api.adminStorage.test 책임. 본 테스트는 page 분기만 가드.
 */

let hookState: {
  data?: AdminStorageOverviewResponse
  isLoading: boolean
  isError: boolean
} = { isLoading: true, isError: false }

vi.mock('@/hooks/useAdminStorageOverview', () => ({
  useAdminStorageOverview: () => hookState,
}))

// AdminGuard 격리 — wave1.5-auditor-admin-ui-access로 페이지가 default
// `<AdminGuard>` 안쪽에 들어갔으므로 children이 렌더되도록 ADMIN role mock.
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

import AdminStoragePage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const RESPONSE: AdminStorageOverviewResponse = {
  overview: {
    totalFiles: 100,
    totalVersions: 200,
    totalBytes: 1024 * 1024,
    trashedFiles: 5,
    trashedBytes: 2048,
    orphanCleanup: {
      lastRunAt: '2026-05-06T14:30:00Z',
      lastDeletedCount: 7,
    },
  },
}

describe('AdminStoragePage', () => {
  beforeEach(() => {
    hookState = { isLoading: true, isError: false }
  })

  it('loading → 로딩 메시지', () => {
    hookState = { isLoading: true, isError: false }
    wrap(<AdminStoragePage />)
    expect(screen.getByText(/불러오는 중/)).toBeTruthy()
  })

  it('error → 에러 메시지(role=alert)', () => {
    hookState = { isLoading: false, isError: true }
    wrap(<AdminStoragePage />)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('success → KPI 카드 + orphan 표', () => {
    hookState = { isLoading: false, isError: false, data: RESPONSE }
    wrap(<AdminStoragePage />)
    expect(screen.getByText('전체 파일')).toBeTruthy()
    expect(screen.getByText('100')).toBeTruthy()
    expect(screen.getByText(/7/)).toBeTruthy()
  })

  it('h1 페이지 제목 존재', () => {
    hookState = { isLoading: false, isError: false, data: RESPONSE }
    wrap(<AdminStoragePage />)
    expect(screen.getByRole('heading', { level: 1, name: /스토리지/ })).toBeTruthy()
  })

  it('성공 시 정리 기록 / 부서별 저장공간 위젯 + v1.x callout 노출', () => {
    hookState = { isLoading: false, isError: false, data: RESPONSE }
    wrap(<AdminStoragePage />)
    // SectionCard 타이틀 — 위젯 2종
    expect(screen.getByRole('heading', { level: 2, name: '정리 기록' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 2, name: '부서별 저장공간' })).toBeTruthy()
    // CleanupList aria-label
    expect(screen.getByLabelText('정리 기록')).toBeTruthy()
    expect(screen.getByLabelText('정리 합계')).toBeTruthy()
    // 부서별 사용량 — 8건 mock (ADMIN_DEPARTMENTS 전체)
    expect(screen.getByText('8개 부서')).toBeTruthy()
    expect(screen.getByText('엔지니어링')).toBeTruthy()
    expect(screen.getByText('법무')).toBeTruthy()
    // v1.x callout
    expect(screen.getByLabelText('storage-mock')).toBeTruthy()
  })

  it('loading/error 상태에서도 위젯/callout 마운트 유지 (실시간 데이터 영역만 분기)', () => {
    hookState = { isLoading: true, isError: false }
    wrap(<AdminStoragePage />)
    // 위젯은 hook 상태와 독립적으로 mock 데이터 렌더
    expect(screen.getByRole('heading', { level: 2, name: '정리 기록' })).toBeTruthy()
    expect(screen.getByLabelText('storage-mock')).toBeTruthy()
  })
})
