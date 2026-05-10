import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TeamMemberTable } from './TeamMemberTable'
import type { TeamMember } from '@/types/team'

const M1: TeamMember = {
  userId: 'u1', displayName: 'Alice', email: 'a@x.io', role: 'OWNER',
  joinedAt: '2026-05-10T00:00:00Z',
}
const M2: TeamMember = {
  userId: 'u2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER',
  joinedAt: '2026-05-10T01:00:00Z',
}

describe('TeamMemberTable', () => {
  it('renders header + member rows (canManage=true → 4 columns, 2 members)', () => {
    render(
      <TeamMemberTable
        members={[M1, M2]}
        currentUserId="u1"
        canManage={true}
        onChangeRole={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('Bob')).toBeTruthy()
    expect(screen.getAllByRole('row')).toHaveLength(3) // header + 2 members
  })

  it('hides action buttons when canManage=false', () => {
    render(
      <TeamMemberTable
        members={[M1, M2]}
        currentUserId="u1"
        canManage={false}
        onChangeRole={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    expect(screen.queryByRole('button', { name: /역할 변경/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /제거/i })).toBeNull()
  })

  it('clicking 역할 변경 calls onChangeRole with member', async () => {
    const onChangeRole = vi.fn()
    const { getAllByRole } = render(
      <TeamMemberTable
        members={[M1, M2]}
        currentUserId="u1"
        canManage={true}
        onChangeRole={onChangeRole}
        onRemove={vi.fn()}
      />,
    )
    const buttons = getAllByRole('button', { name: /역할 변경/i })
    buttons[1].click() // Bob's row
    expect(onChangeRole).toHaveBeenCalledWith(M2)
  })
})
