import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RemoveMemberDialog } from './RemoveMemberDialog'
import { api } from '@/lib/api'
import type { TeamMember } from '@/types/team'

vi.mock('@/lib/api', () => ({ api: { removeTeamMember: vi.fn() } }))

const M: TeamMember = {
  userId: 'u-2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER',
  joinedAt: '2026-05-10T00:00:00Z',
}

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('RemoveMemberDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('confirms and removes member', async () => {
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const onClose = vi.fn()
    render(wrap(<RemoveMemberDialog teamId="team-1" member={M} onClose={onClose} />))
    fireEvent.click(screen.getByRole('button', { name: /제거/ }))
    await waitFor(() => expect(api.removeTeamMember).toHaveBeenCalledWith('team-1', 'u-2'))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('TEAM_OWNER_REQUIRED 에러 inline', async () => {
    const err = Object.assign(new Error('removeTeamMember failed: 400'), {
      status: 400, code: 'TEAM_OWNER_REQUIRED',
    })
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    render(wrap(<RemoveMemberDialog teamId="team-1" member={{ ...M, role: 'OWNER' }} onClose={vi.fn()} />))
    fireEvent.click(screen.getByRole('button', { name: /제거/ }))
    await waitFor(() => {
      const alert = screen.getByRole('alert')
      expect(alert.textContent).toMatch(/최소 한 명의 OWNER/)
    })
  })
})
