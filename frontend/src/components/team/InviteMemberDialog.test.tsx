import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { InviteMemberDialog } from './InviteMemberDialog'

// Mock api so the embedded UserSearchCombobox's useUserSearch + the mutation are stubbed.
vi.mock('@/lib/api', () => ({
  api: {
    inviteTeamMember: vi.fn(),
    searchUsers: vi.fn().mockResolvedValue({ items: [] }),
  },
}))

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('InviteMemberDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders dialog with role=ARIA + UserSearchCombobox', () => {
    render(wrap(<InviteMemberDialog teamId="team-1" onClose={vi.fn()} />))
    expect(screen.getByRole('dialog', { name: /멤버 초대/ })).toBeTruthy()
  })

  it('Esc closes dialog', () => {
    const onClose = vi.fn()
    render(wrap(<InviteMemberDialog teamId="team-1" onClose={onClose} />))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })
})
