import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, act, within } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type {
  AdminPermissionFilters,
  AdminPermissionPage,
  AdminPermissionRow,
} from '@/types/permission'

const { revokeMutate } = vi.hoisted(() => ({ revokeMutate: vi.fn() }))

/**
 * /admin/permissions — 권한 매트릭스 (admin-permission-matrix, Wave 2 T5).
 *
 * <p>read-only viewer — admin-department-crud page.test.tsx 매트릭스 mirror.
 * fetch wire는 hook.test/api.test에서 검증 — 본 테스트는 hook 모킹 후 UI 매트릭스만 가드.
 */

let lastFilters: AdminPermissionFilters = {}
let queryState: {
  data?: AdminPermissionPage
  isLoading: boolean
  isError: boolean
} = { isLoading: false, isError: false }

vi.mock('@/hooks/useAdminPermissions', () => ({
  useAdminPermissions: (filters: AdminPermissionFilters) => {
    lastFilters = filters
    return queryState
  },
}))

vi.mock('@/hooks/useAdminRevokePermission', () => ({
  useAdminRevokePermission: () => ({ mutate: revokeMutate, isPending: false }),
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

import AdminPermissionsPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const ROW_LIVE: AdminPermissionRow = {
  id: '11111111-1111-1111-1111-111111111111',
  subjectType: 'user',
  subjectId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  subjectName: 'Alice',
  resourceType: 'folder',
  resourceId: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
  resourceName: 'Reports',
  preset: 'read',
  grantedByActorId: 'gggggggg-gggg-gggg-gggg-gggggggggggg',
  grantedByName: 'Granter',
  grantedAt: '2026-04-01T00:00:00Z',
  expiresAt: null,
  isExpired: false,
}

const ROW_EXPIRED: AdminPermissionRow = {
  id: '22222222-2222-2222-2222-222222222222',
  subjectType: 'department',
  subjectId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  subjectName: 'Sales',
  resourceType: 'file',
  resourceId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
  resourceName: 'Budget.xlsx',
  preset: 'edit',
  grantedByActorId: 'gggggggg-gggg-gggg-gggg-gggggggggggg',
  grantedByName: 'Granter',
  grantedAt: '2026-04-01T00:00:00Z',
  expiresAt: '2026-04-15T00:00:00Z',
  isExpired: true,
}

const PAGE_DATA: AdminPermissionPage = {
  content: [ROW_LIVE, ROW_EXPIRED],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
}

describe('AdminPermissionsPage — 렌더 + 필터', () => {
  beforeEach(() => {
    vi.useRealTimers()
    queryState = { isLoading: false, isError: false, data: PAGE_DATA }
    lastFilters = {}
    revokeMutate.mockReset()
  })

  it('초기 렌더 — 5개 필터 + 테이블 헤더', () => {
    wrap(<AdminPermissionsPage />)
    expect(screen.getByLabelText('대상 종류 필터')).toBeTruthy()
    expect(screen.getByLabelText('대상 ID 필터')).toBeTruthy()
    expect(screen.getByLabelText('리소스 종류 필터')).toBeTruthy()
    expect(screen.getByLabelText('프리셋 필터')).toBeTruthy()
    expect(screen.getByLabelText('이름 검색')).toBeTruthy()
    expect(screen.getByText('대상')).toBeTruthy()
    expect(screen.getByText('리소스')).toBeTruthy()
    expect(screen.getByText('권한')).toBeTruthy()
  })

  it('데이터 행 렌더 — 라이브 grant', () => {
    wrap(<AdminPermissionsPage />)
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('Reports')).toBeTruthy()
    expect(screen.getAllByText('read').length).toBeGreaterThan(0)
  })

  it('만료 배지 — isExpired=true 인 행에 "만료됨" 노출', () => {
    wrap(<AdminPermissionsPage />)
    expect(screen.getByText('만료됨')).toBeTruthy()
  })

  it('subjectType 필터 변경 — 즉시 hook 인자 갱신', () => {
    wrap(<AdminPermissionsPage />)
    fireEvent.change(screen.getByLabelText('대상 종류 필터'), {
      target: { value: 'user' },
    })
    expect(lastFilters.subjectType).toBe('user')
  })

  it('preset 필터 변경 — 즉시 hook 인자 갱신', () => {
    wrap(<AdminPermissionsPage />)
    fireEvent.change(screen.getByLabelText('프리셋 필터'), {
      target: { value: 'admin' },
    })
    expect(lastFilters.preset).toBe('admin')
  })

  it('q 검색 입력 — 300ms debounce 후 갱신', () => {
    vi.useFakeTimers()
    wrap(<AdminPermissionsPage />)

    fireEvent.change(screen.getByLabelText('이름 검색'), {
      target: { value: 'alice' },
    })
    // debounce 이전 — 이전 q 유지
    expect(lastFilters.q).toBeUndefined()

    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(lastFilters.q).toBe('alice')
    vi.useRealTimers()
  })

  it('빈 목록 — 안내 메시지', () => {
    queryState = {
      isLoading: false,
      isError: false,
      data: { ...PAGE_DATA, content: [], totalElements: 0 },
    }
    wrap(<AdminPermissionsPage />)
    expect(screen.getByText('조건에 맞는 권한이 없습니다.')).toBeTruthy()
  })

  it('로딩 상태', () => {
    queryState = { isLoading: true, isError: false }
    wrap(<AdminPermissionsPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('에러 상태 — alert', () => {
    queryState = { isLoading: false, isError: true }
    wrap(<AdminPermissionsPage />)
    const alerts = screen.getAllByRole('alert')
    expect(alerts.some((a) => /목록을 불러오지 못했습니다/.test(a.textContent ?? ''))).toBe(true)
  })

  it('이름 없음 — subjectName/resourceName/grantedByName null fallback', () => {
    const ROW_NULL: AdminPermissionRow = {
      ...ROW_LIVE,
      id: '33333333-3333-3333-3333-333333333333',
      subjectName: null,
      resourceName: null,
      grantedByName: null,
    }
    queryState = {
      isLoading: false,
      isError: false,
      data: { ...PAGE_DATA, content: [ROW_NULL], totalElements: 1 },
    }
    wrap(<AdminPermissionsPage />)
    // "(이름 없음)" 텍스트가 최소 1회 이상 등장
    expect(screen.getAllByText('(이름 없음)').length).toBeGreaterThanOrEqual(2)
  })

  // ── admin-permission-revoke (Wave 2 T5 follow-up) ───────────────────────

  it('각 행에 "철회" 버튼 노출', () => {
    wrap(<AdminPermissionsPage />)
    // PAGE_DATA에 2 row → 각 행마다 "철회" 버튼 1개씩.
    expect(screen.getAllByRole('button', { name: /철회$/ })).toHaveLength(2)
  })

  it('철회 버튼 → ConfirmDialog 노출 + 확인 시 mutate(rowId) + 다이얼로그 닫힘', () => {
    wrap(<AdminPermissionsPage />)

    const liveRowButton = screen.getByRole('button', {
      name: /Alice.*Reports.*read.*철회/,
    })
    fireEvent.click(liveRowButton)

    const dialog = screen.getByRole('dialog', { name: /권한을 철회/ })
    expect(dialog).toBeTruthy()
    expect(within(dialog).getByText(/즉시 회수합니다/)).toBeTruthy()

    fireEvent.click(within(dialog).getByRole('button', { name: '철회' }))

    expect(revokeMutate).toHaveBeenCalledWith(ROW_LIVE.id)
    // 확인 후 다이얼로그가 즉시 닫힌다(mutation pending 동안 banner 등 별도 UX 없음).
    expect(screen.queryByRole('dialog', { name: /권한을 철회/ })).toBeNull()
  })

  it('취소 클릭 시 mutate 호출 없음 + 다이얼로그 닫힘', () => {
    wrap(<AdminPermissionsPage />)

    fireEvent.click(
      screen.getByRole('button', { name: /Alice.*Reports.*read.*철회/ }),
    )
    const dialog = screen.getByRole('dialog', { name: /권한을 철회/ })
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))

    expect(revokeMutate).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog', { name: /권한을 철회/ })).toBeNull()
  })

  it('만료된 grant도 동일한 "철회" 버튼으로 정리 가능', () => {
    wrap(<AdminPermissionsPage />)

    const expiredButton = screen.getByRole('button', {
      name: /Sales.*Budget\.xlsx.*edit.*철회/,
    })
    fireEvent.click(expiredButton)

    const dialog = screen.getByRole('dialog', { name: /권한을 철회/ })
    fireEvent.click(within(dialog).getByRole('button', { name: '철회' }))

    expect(revokeMutate).toHaveBeenCalledWith(ROW_EXPIRED.id)
  })
})
