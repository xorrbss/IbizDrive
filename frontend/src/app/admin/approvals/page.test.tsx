import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type {
  AdminApprovalDto,
  AdminApprovalFilters,
  AdminApprovalPage,
} from '@/types/admin-approval'

/**
 * /admin/approvals — dual-approval framework Phase 4 (ADR #47).
 *
 * <p>read-only viewer 매트릭스 — hook 모킹 후 UI 정합만 가드.
 * fetch wire는 api.adminApprovals.test.ts에서 검증.
 */

let lastFilters: AdminApprovalFilters = {}
let queryState: {
  data?: AdminApprovalPage
  isLoading: boolean
  isError: boolean
} = { isLoading: false, isError: false }

vi.mock('@/hooks/useAdminApprovals', () => ({
  useAdminApprovals: (filters: AdminApprovalFilters) => {
    lastFilters = filters
    return queryState
  },
}))

vi.mock('@/hooks/useAdminApprovalDecision', () => ({
  useApproveApproval: () => ({ mutate: vi.fn(), isPending: false }),
  useRejectApproval: () => ({ mutate: vi.fn(), isPending: false }),
  useCancelApproval: () => ({ mutate: vi.fn(), isPending: false }),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))

const CURRENT_USER_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({
    data: {
      user: {
        id: CURRENT_USER_ID,
        email: 'a@b.com',
        name: 'A',
        kind: 'human',
        mustChangePassword: false,
      },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'k',
    },
    isLoading: false,
    isError: false,
  }),
}))

import AdminApprovalsPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const ROW_OTHER: AdminApprovalDto = {
  id: '11111111-1111-1111-1111-111111111111',
  actionType: 'role_change',
  payloadJson: '{}',
  requestedBy: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  requestedAt: '2026-05-13T00:00:00Z',
  status: 'REQUESTED',
  secondaryApproverId: null,
  decidedAt: null,
  decisionReason: null,
  expiresAt: '2026-05-15T00:00:00Z',
}

const ROW_MINE: AdminApprovalDto = {
  id: '22222222-2222-2222-2222-222222222222',
  actionType: 'trash_purge',
  payloadJson: '{}',
  requestedBy: CURRENT_USER_ID,
  requestedAt: '2026-05-13T00:00:00Z',
  status: 'REQUESTED',
  secondaryApproverId: null,
  decidedAt: null,
  decisionReason: null,
  expiresAt: '2026-05-15T00:00:00Z',
}

const PAGE_DATA: AdminApprovalPage = {
  content: [ROW_OTHER, ROW_MINE],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 50,
}

describe('AdminApprovalsPage — 렌더 + 필터', () => {
  beforeEach(() => {
    queryState = { isLoading: false, isError: false, data: PAGE_DATA }
    lastFilters = {}
  })

  it('초기 렌더 — 필터 + 테이블 헤더', () => {
    wrap(<AdminApprovalsPage />)
    expect(screen.getByLabelText('작업 유형 필터')).toBeTruthy()
    // "작업 유형"은 filter label span + table th 양쪽에 등장 — 양쪽 모두 노출되는지만 확인
    expect(screen.getAllByText('작업 유형').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('요청자')).toBeTruthy()
    expect(screen.getByText('상태')).toBeTruthy()
  })

  it('데이터 행 렌더 — actionType 한글 라벨', () => {
    wrap(<AdminApprovalsPage />)
    // filter <option> 과 row td 양쪽에 같은 텍스트가 등장 — 행 td만 선택
    const rows = screen.getAllByText('역할 변경')
    expect(rows.length).toBeGreaterThan(0)
    expect(screen.getAllByText('휴지통 영구 삭제').length).toBeGreaterThan(0)
  })

  it('자신의 요청 — "내 요청" 라벨 + 취소 버튼만 노출', () => {
    wrap(<AdminApprovalsPage />)
    expect(screen.getByText('내 요청')).toBeTruthy()
    // 본인 요청 행에는 승인/거부 버튼 없음
    expect(
      screen.queryByRole('button', { name: /휴지통 영구 삭제 승인/ }),
    ).toBeNull()
    expect(
      screen.queryByRole('button', { name: /휴지통 영구 삭제 거부/ }),
    ).toBeNull()
    // 취소 버튼 노출
    expect(
      screen.getByRole('button', { name: /휴지통 영구 삭제 요청 취소/ }),
    ).toBeTruthy()
  })

  it('타인 요청 — 승인/거부 버튼 노출', () => {
    wrap(<AdminApprovalsPage />)
    expect(screen.getByRole('button', { name: /역할 변경 승인/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /역할 변경 거부/ })).toBeTruthy()
  })

  it('actionType 필터 변경 — 즉시 hook 인자 갱신', () => {
    wrap(<AdminApprovalsPage />)
    fireEvent.change(screen.getByLabelText('작업 유형 필터'), {
      target: { value: 'role_change' },
    })
    expect(lastFilters.actionType).toBe('role_change')
  })

  it('빈 목록 — 안내 메시지', () => {
    queryState = {
      isLoading: false,
      isError: false,
      data: { ...PAGE_DATA, content: [], totalElements: 0 },
    }
    wrap(<AdminApprovalsPage />)
    expect(screen.getByText('대기 중인 승인 요청이 없습니다.')).toBeTruthy()
  })

  it('로딩 상태', () => {
    queryState = { isLoading: true, isError: false }
    wrap(<AdminApprovalsPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('에러 상태 — alert', () => {
    queryState = { isLoading: false, isError: true }
    wrap(<AdminApprovalsPage />)
    const alerts = screen.getAllByRole('alert')
    expect(
      alerts.some((a) =>
        /승인 목록을 불러오지 못했습니다/.test(a.textContent ?? ''),
      ),
    ).toBe(true)
  })

  it('승인 클릭 → DecisionDialog (sender ≠ me)', () => {
    wrap(<AdminApprovalsPage />)
    fireEvent.click(screen.getByRole('button', { name: /역할 변경 승인/ }))
    expect(screen.getByRole('dialog', { name: /요청을 승인/ })).toBeTruthy()
    // approve 다이얼로그는 사유 optional — 빈 사유로도 제출 가능
    const confirmBtn = screen.getByRole('button', { name: /^승인$/ })
    expect(confirmBtn.hasAttribute('disabled')).toBe(false)
  })

  it('거부 클릭 → DecisionDialog + 사유 비어있으면 확인 disabled', () => {
    wrap(<AdminApprovalsPage />)
    fireEvent.click(screen.getByRole('button', { name: /역할 변경 거부/ }))
    expect(screen.getByRole('dialog', { name: /요청을 거부/ })).toBeTruthy()
    const confirmBtn = screen.getByRole('button', { name: /^거부$/ })
    expect(confirmBtn.hasAttribute('disabled')).toBe(true)

    // 사유 입력 후 활성화
    fireEvent.change(screen.getByLabelText('결정 사유'), {
      target: { value: 'denied' },
    })
    expect(confirmBtn.hasAttribute('disabled')).toBe(false)
  })

  it('terminal status — "처리됨" 라벨, 액션 버튼 없음', () => {
    queryState = {
      isLoading: false,
      isError: false,
      data: {
        ...PAGE_DATA,
        content: [{ ...ROW_OTHER, status: 'APPROVED', decidedAt: '2026-05-13T01:00:00Z' }],
        totalElements: 1,
      },
    }
    wrap(<AdminApprovalsPage />)
    expect(screen.getByText('처리됨')).toBeTruthy()
    expect(
      screen.queryByRole('button', { name: /역할 변경 승인/ }),
    ).toBeNull()
  })
})
