import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ClientMembersPage } from './ClientMembersPage'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: {
    getTeamMembers: vi.fn(),
    inviteTeamMember: vi.fn(),
    removeTeamMember: vi.fn(),
    changeTeamMemberRole: vi.fn(),
  },
}))

vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({ data: { user: { id: 'u1' } } }),
}))

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('ClientMembersPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders member table after fetch', async () => {
    ;(api.getTeamMembers as ReturnType<typeof vi.fn>).mockResolvedValue([
      { userId: 'u1', displayName: 'Alice', email: 'a@x.io', role: 'OWNER', joinedAt: '2026-05-10T00:00:00Z' },
      { userId: 'u2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER', joinedAt: '2026-05-10T01:00:00Z' },
    ])
    render(wrap(<ClientMembersPage teamId="team-1" />))
    await waitFor(() => {
      expect(screen.getByText('Alice')).toBeTruthy()
      expect(screen.getByText('Bob')).toBeTruthy()
    })
  })

  it('403 → forbidden state', async () => {
    const err = Object.assign(new Error('getTeamMembers failed: 403'), {
      status: 403, code: 'PERMISSION_DENIED',
    })
    ;(api.getTeamMembers as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    render(wrap(<ClientMembersPage teamId="team-1" />))
    await waitFor(() => {
      expect(screen.getByText(/접근 권한이 없습니다/)).toBeTruthy()
    })
  })
})
