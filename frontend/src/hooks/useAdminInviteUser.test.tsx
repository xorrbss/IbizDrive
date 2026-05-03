import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminInviteUser } from './useAdminInviteUser'
import { api, type AdminInvitedUser } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { adminInviteUser: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const INVITED: AdminInvitedUser = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'bob@example.com',
  displayName: 'Bob',
  role: 'MEMBER',
  mustChangePassword: true,
}

describe('useAdminInviteUser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.adminInviteUser 호출 + data 반환', async () => {
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockResolvedValue(INVITED)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const { result } = renderHook(() => useAdminInviteUser(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ email: 'bob@example.com', displayName: 'Bob', role: 'MEMBER' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminInviteUser).toHaveBeenCalledWith({
      email: 'bob@example.com',
      displayName: 'Bob',
      role: 'MEMBER',
    })
    expect(result.current.data).toEqual(INVITED)
  })

  it('409 DUPLICATE_EMAIL → isError + error.code 보존', async () => {
    const err = Object.assign(new Error('adminInviteUser failed: 409'), {
      status: 409,
      code: 'CONFLICT',
      reason: 'DUPLICATE_EMAIL',
    })
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const { result } = renderHook(() => useAdminInviteUser(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ email: 'dup@example.com', displayName: 'Dup', role: 'MEMBER' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    const e = result.current.error as Error & { status?: number; code?: string; reason?: string }
    expect(e.status).toBe(409)
    expect(e.code).toBe('CONFLICT')
    expect(e.reason).toBe('DUPLICATE_EMAIL')
  })
})
