import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useRemoveTeamMember } from './useRemoveTeamMember'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({ api: { removeTeamMember: vi.fn() } }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useRemoveTeamMember', () => {
  beforeEach(() => vi.clearAllMocks())

  it('removes and invalidates qk.teams.members(teamId)', async () => {
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRemoveTeamMember('team-1'), { wrapper: wrap(qc) })

    act(() => result.current.mutate({ userId: 'user-2' }))
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(api.removeTeamMember).toHaveBeenCalledWith('team-1', 'user-2')
    expect(spy).toHaveBeenCalledWith({ queryKey: qk.teams.members('team-1') })
  })

  it('400 TEAM_OWNER_REQUIRED → isError + skip invalidate', async () => {
    const err = Object.assign(new Error('removeTeamMember failed: 400'), {
      status: 400,
      code: 'TEAM_OWNER_REQUIRED',
    })
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRemoveTeamMember('team-1'), { wrapper: wrap(qc) })

    act(() => result.current.mutate({ userId: 'user-2' }))
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { code?: string }).code).toBe('TEAM_OWNER_REQUIRED')
    expect(spy).not.toHaveBeenCalled()
  })
})
