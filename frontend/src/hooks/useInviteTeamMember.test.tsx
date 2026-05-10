import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useInviteTeamMember } from './useInviteTeamMember'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({ api: { inviteTeamMember: vi.fn() } }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useInviteTeamMember', () => {
  beforeEach(() => vi.clearAllMocks())

  it('invites and invalidates qk.teams.members(teamId)', async () => {
    ;(api.inviteTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useInviteTeamMember('team-1'), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ userId: 'user-2' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.inviteTeamMember).toHaveBeenCalledWith('team-1', 'user-2')
    expect(spy).toHaveBeenCalledWith({ queryKey: qk.teams.members('team-1') })
  })
})
