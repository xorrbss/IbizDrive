import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ChangeRoleDialog } from './ChangeRoleDialog'
import { api } from '@/lib/api'
import type { TeamMember } from '@/types/team'

vi.mock('@/lib/api', () => ({ api: { changeTeamMemberRole: vi.fn() } }))

const M: TeamMember = {
  userId: 'u-2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER',
  joinedAt: '2026-05-10T00:00:00Z',
}

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('ChangeRoleDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submits with selected new role (MEMBER → OWNER)', async () => {
    ;(api.changeTeamMemberRole as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const onClose = vi.fn()
    render(wrap(<ChangeRoleDialog teamId="team-1" member={M} onClose={onClose} />))

    fireEvent.click(screen.getByRole('button', { name: /변경/ }))
    await waitFor(() => expect(api.changeTeamMemberRole).toHaveBeenCalledWith('team-1', 'u-2', 'OWNER'))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('TEAM_OWNER_REQUIRED 에러 inline 표시', async () => {
    const err = Object.assign(new Error('changeTeamMemberRole failed: 400'), {
      status: 400, code: 'TEAM_OWNER_REQUIRED',
    })
    ;(api.changeTeamMemberRole as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    render(wrap(<ChangeRoleDialog teamId="team-1" member={{ ...M, role: 'OWNER' }} onClose={vi.fn()} />))

    fireEvent.click(screen.getByRole('button', { name: /변경/ }))
    await waitFor(() => {
      const alert = screen.getByRole('alert')
      expect(alert.textContent).toMatch(/최소 한 명의 OWNER/)
    })
  })
})
