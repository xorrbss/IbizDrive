import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminUpdateUserQuota } from './useAdminUpdateUserQuota'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * {@link useAdminUpdateUserQuota} 단위 — quota mutation Phase 4 (`docs/04 §6.1`).
 *
 * <p>{@link useAdminUpdateUser} 테스트 패턴 답습. 성공 시 admin users prefix 무효화, 실패 시 status
 * 보존.
 */

vi.mock('@/lib/api', () => ({
  api: { adminUpdateUserQuota: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const USER_ID = '11111111-1111-1111-1111-111111111111'
const NEW_QUOTA = 20 * 1024 * 1024 * 1024

describe('useAdminUpdateUserQuota', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.adminUpdateUserQuota 호출 + adminUsers 캐시 무효화', async () => {
    ;(api.adminUpdateUserQuota as ReturnType<typeof vi.fn>).mockResolvedValue({
      storageQuota: NEW_QUOTA,
      storageUsed: 0,
    })
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    qc.setQueryData(qk.adminUsersList(0, 50), { content: [], totalElements: 0 })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useAdminUpdateUserQuota(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: USER_ID, storageQuota: NEW_QUOTA })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminUpdateUserQuota).toHaveBeenCalledWith(USER_ID, NEW_QUOTA)
    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: qk.adminUsers() }),
    )
  })

  it('404 USER_NOT_FOUND → isError + error.status 보존', async () => {
    const err = Object.assign(new Error('adminUpdateUserQuota failed: 404'), {
      status: 404,
      code: 'NOT_FOUND',
      reason: 'USER_NOT_FOUND',
    })
    ;(api.adminUpdateUserQuota as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const { result } = renderHook(() => useAdminUpdateUserQuota(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: USER_ID, storageQuota: NEW_QUOTA })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    const e = result.current.error as Error & { status?: number; reason?: string }
    expect(e.status).toBe(404)
    expect(e.reason).toBe('USER_NOT_FOUND')
  })

  it('400 VALIDATION_ERROR — 음수 storageQuota 거부 (backend 가드, hook은 그대로 전달)', async () => {
    const err = Object.assign(new Error('adminUpdateUserQuota failed: 400'), {
      status: 400,
      code: 'VALIDATION_ERROR',
    })
    ;(api.adminUpdateUserQuota as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const { result } = renderHook(() => useAdminUpdateUserQuota(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: USER_ID, storageQuota: -1 })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    const e = result.current.error as Error & { status?: number; code?: string }
    expect(e.status).toBe(400)
    expect(e.code).toBe('VALIDATION_ERROR')
  })
})
