import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminInviteUser } from './useAdminInviteUser'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { adminInviteUser: vi.fn() },
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

/**
 * ADR #21 admin closure (P3) — `useAdminInviteUser`는 `api.adminInviteUser` 단순 위임 mutation.
 * 캐시 무효화/store 갱신 없음 — 페이지가 onSuccess로 폼 reset/토스트만 처리.
 */
describe('useAdminInviteUser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('mutate(payload) → api.adminInviteUser 호출 + 응답 그대로 data', async () => {
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
      mustChangePassword: true,
    })
    const qc = new QueryClient()
    const { result } = renderHook(() => useAdminInviteUser(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        email: 'alice@example.com',
        displayName: 'Alice',
        role: 'MEMBER',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminInviteUser).toHaveBeenCalledWith({
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
    })
    expect(result.current.data).toMatchObject({
      email: 'alice@example.com',
      mustChangePassword: true,
    })
  })

  it('409 DUPLICATE_EMAIL → isError + error.code 보존', async () => {
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 409,
      code: 'CONFLICT',
      reason: 'DUPLICATE_EMAIL',
    })
    const qc = new QueryClient()
    const { result } = renderHook(() => useAdminInviteUser(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        email: 'dup@example.com',
        displayName: 'Dup',
        role: 'MEMBER',
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toMatchObject({ status: 409, reason: 'DUPLICATE_EMAIL' })
  })
})
