import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTeamMembers } from './useTeamMembers'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { getTeamMembers: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useTeamMembers', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetches members for a teamId', async () => {
    ;(api.getTeamMembers as ReturnType<typeof vi.fn>).mockResolvedValue([
      { userId: 'u1', displayName: 'Alice', email: 'a@x.io', role: 'OWNER', joinedAt: '2026-05-10T00:00:00Z' },
    ])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTeamMembers('team-1'), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getTeamMembers).toHaveBeenCalledWith('team-1')
    expect(result.current.data).toHaveLength(1)
  })

  it('skips fetch when teamId is null', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useTeamMembers(null), { wrapper: wrap(qc) })
    expect(api.getTeamMembers).not.toHaveBeenCalled()
  })
})
