import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminUpdateUser } from './useAdminUpdateUser'
import { api, type AdminUserSummary } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { ApprovalRequiredError } from '@/lib/errors'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'

vi.mock('@/lib/api', () => ({
  api: { adminUpdateUser: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const SUMMARY: AdminUserSummary = {
  id: '22222222-2222-2222-2222-222222222222',
  email: 'bob@example.com',
  displayName: 'Bob',
  role: 'AUDITOR',
  isActive: true,
  createdAt: '2026-01-02T00:00:00Z',
  lastLoginAt: null,
  storageQuota: 10 * 1024 * 1024 * 1024,
  storageUsed: 0,
}

describe('useAdminUpdateUser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
  })

  it('성공 → api.adminUpdateUser 호출 + adminUsers 캐시 무효화', async () => {
    ;(api.adminUpdateUser as ReturnType<typeof vi.fn>).mockResolvedValue(SUMMARY)
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    // 시드: 기존 list 캐시 존재
    qc.setQueryData(qk.adminUsersList(0, 50), { content: [], totalElements: 0 })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useAdminUpdateUser(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: SUMMARY.id, body: { role: 'AUDITOR' } })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminUpdateUser).toHaveBeenCalledWith(SUMMARY.id, { role: 'AUDITOR' })
    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: qk.adminUsers() }),
    )
  })

  it('403 SELF_PROTECTION → isError + error.reason 보존', async () => {
    const err = Object.assign(new Error('adminUpdateUser failed: 403'), {
      status: 403,
      code: 'FORBIDDEN',
      reason: 'SELF_PROTECTION',
    })
    ;(api.adminUpdateUser as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const { result } = renderHook(() => useAdminUpdateUser(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: SUMMARY.id, body: { role: 'MEMBER' } })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    const e = result.current.error as Error & { status?: number; code?: string; reason?: string }
    expect(e.status).toBe(403)
    expect(e.reason).toBe('SELF_PROTECTION')
  })

  it('202 APPROVAL_REQUIRED → ApprovalRequiredError + toast.info + cache invalidate 미호출', async () => {
    const apprErr = new ApprovalRequiredError(
      'aaaa-bbbb-cccc-dddd',
      '2026-05-15T00:00:00Z',
      '이 작업은 2인 승인이 필요합니다',
    )
    ;(api.adminUpdateUser as ReturnType<typeof vi.fn>).mockRejectedValue(apprErr)
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useAdminUpdateUser(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: SUMMARY.id, body: { role: 'ADMIN' } })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(ApprovalRequiredError)
    // adminUsers 캐시 무효화 미호출 — onSuccess 분기 미도달
    expect(
      invalidateSpy.mock.calls.find(
        ([arg]) =>
          arg &&
          typeof arg === 'object' &&
          'queryKey' in arg &&
          JSON.stringify((arg as { queryKey: unknown }).queryKey) ===
            JSON.stringify(qk.adminUsers()),
      ),
    ).toBeUndefined()
    // toast.info 호출 (사용자 역할 변경 액션 라벨)
    await waitFor(() => {
      const calls = toastSpy('info').mock.calls
      expect(calls.length).toBeGreaterThan(0)
      expect(calls[0][0]).toMatch(/승인 요청이 등록되었습니다/)
      expect(calls[0][0]).toMatch(/사용자 역할 변경/)
    })
  })
})
