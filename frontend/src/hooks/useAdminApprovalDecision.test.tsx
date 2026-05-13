import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import {
  useApproveApproval,
  useCancelApproval,
  useRejectApproval,
} from './useAdminApprovalDecision'
import * as apiModule from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AdminApprovalDto } from '@/types/admin-approval'

/**
 * dual-approval Phase 4 — 3 mutation hook의 onSuccess invalidation 가드.
 *
 * <p>approve 분기 actionType별 부수 invalidation:
 * <ul>
 *   <li>role_change → adminUsers()</li>
 *   <li>retention_change → adminTrashPolicy()</li>
 *   <li>trash_purge → adminTrash()</li>
 * </ul>
 */

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    approveAdminApproval: vi.fn(),
    rejectAdminApproval: vi.fn(),
    cancelAdminApproval: vi.fn(),
  }
})

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

function makeDto(actionType: string, status: AdminApprovalDto['status'] = 'APPROVED'): AdminApprovalDto {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    actionType,
    payloadJson: '{}',
    requestedBy: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    requestedAt: '2026-05-13T00:00:00Z',
    status,
    secondaryApproverId: null,
    decidedAt: '2026-05-13T01:00:00Z',
    decisionReason: null,
    expiresAt: '2026-05-15T00:00:00Z',
  }
}

describe('useApproveApproval', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 adminApprovals + detail invalidate', async () => {
    ;(apiModule.approveAdminApproval as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeDto('role_change'),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useApproveApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({ id: 'approval-1' })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const calls = spy.mock.calls.map((c) => c[0]?.queryKey)
    expect(calls).toContainEqual(qk.adminApprovals())
    expect(calls).toContainEqual(
      qk.adminApproval('11111111-1111-1111-1111-111111111111'),
    )
  })

  it('role_change approve → adminUsers 무효화', async () => {
    ;(apiModule.approveAdminApproval as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeDto('role_change'),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useApproveApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({ id: 'a' })
    const calls = spy.mock.calls.map((c) => c[0]?.queryKey)
    expect(calls).toContainEqual(qk.adminUsers())
  })

  it('retention_change approve → adminTrashPolicy 무효화', async () => {
    ;(apiModule.approveAdminApproval as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeDto('retention_change'),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useApproveApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({ id: 'a' })
    const calls = spy.mock.calls.map((c) => c[0]?.queryKey)
    expect(calls).toContainEqual(qk.adminTrashPolicy())
  })

  it('trash_purge approve → adminTrash 무효화', async () => {
    ;(apiModule.approveAdminApproval as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeDto('trash_purge'),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useApproveApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({ id: 'a' })
    const calls = spy.mock.calls.map((c) => c[0]?.queryKey)
    expect(calls).toContainEqual(qk.adminTrash())
  })

  it('decisionReason body 전달', async () => {
    const fn = apiModule.approveAdminApproval as ReturnType<typeof vi.fn>
    fn.mockResolvedValue(makeDto('role_change'))
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useApproveApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({
      id: 'a',
      body: { decisionReason: 'ok' },
    })
    expect(fn).toHaveBeenCalledWith('a', { decisionReason: 'ok' })
  })
})

describe('useRejectApproval', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 adminApprovals invalidate + 부수 키 없음', async () => {
    ;(apiModule.rejectAdminApproval as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeDto('role_change', 'REJECTED'),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRejectApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({
      id: 'a',
      body: { decisionReason: 'no' },
    })
    const calls = spy.mock.calls.map((c) => c[0]?.queryKey)
    expect(calls).toContainEqual(qk.adminApprovals())
    // 부수 invalidation 없음 (reject은 action 미실행)
    expect(calls).not.toContainEqual(qk.adminUsers())
    expect(calls).not.toContainEqual(qk.adminTrashPolicy())
    expect(calls).not.toContainEqual(qk.adminTrash())
  })

  it('body 그대로 전달', async () => {
    const fn = apiModule.rejectAdminApproval as ReturnType<typeof vi.fn>
    fn.mockResolvedValue(makeDto('role_change', 'REJECTED'))
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useRejectApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({
      id: 'a',
      body: { decisionReason: 'denied' },
    })
    expect(fn).toHaveBeenCalledWith('a', { decisionReason: 'denied' })
  })
})

describe('useCancelApproval', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 adminApprovals invalidate + 부수 키 없음', async () => {
    ;(apiModule.cancelAdminApproval as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeDto('trash_purge', 'CANCELLED'),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCancelApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync('a')
    const calls = spy.mock.calls.map((c) => c[0]?.queryKey)
    expect(calls).toContainEqual(qk.adminApprovals())
    // cancel은 action 미실행 — adminTrash 등 부수 키 무효화 없음
    expect(calls).not.toContainEqual(qk.adminTrash())
  })

  it('id만 전달', async () => {
    const fn = apiModule.cancelAdminApproval as ReturnType<typeof vi.fn>
    fn.mockResolvedValue(makeDto('role_change', 'CANCELLED'))
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useCancelApproval(), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync('approval-xyz')
    expect(fn).toHaveBeenCalledWith('approval-xyz')
  })
})
