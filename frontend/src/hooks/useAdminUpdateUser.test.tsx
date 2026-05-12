import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminUpdateUser } from './useAdminUpdateUser'
import { api, type AdminUserSummary } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

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
})
