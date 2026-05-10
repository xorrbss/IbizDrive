import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { TeamDetail } from './TeamDetail'
import type { AdminTeamDetail } from '@/lib/api'
import type { TeamMember } from '@/types/team'

const TEAM: AdminTeamDetail = {
  id: 't-1',
  name: '디자인 시스템',
  description: '컴포넌트와 토큰 관리',
  color: '#5B7FCC',
  leadId: 'u-1',
  visibility: 'private',
  rootFolderId: 'f-1',
  memberCount: 2,
  archived: false,
  archivedAt: null,
  archivedBy: null,
  createdBy: 'u-1',
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-01T00:00:00Z',
}

const MEMBERS: TeamMember[] = [
  {
    userId: 'u-1',
    displayName: 'Alice',
    email: 'alice@example.com',
    role: 'OWNER',
    joinedAt: '2026-05-01T00:00:00Z',
  },
  {
    userId: 'u-2',
    displayName: 'Bob',
    email: 'bob@example.com',
    role: 'MEMBER',
    joinedAt: '2026-05-02T00:00:00Z',
  },
]

describe('TeamDetail', () => {
  it('팀 이름/설명/멤버 수/리더 표시', () => {
    render(<TeamDetail team={TEAM} members={MEMBERS} />)
    expect(screen.getByRole('heading', { level: 2, name: '디자인 시스템' })).toBeTruthy()
    expect(screen.getByText('컴포넌트와 토큰 관리')).toBeTruthy()
    // memberCount는 stat row와 멤버 섹션 모두 — getAll
    expect(screen.getAllByText('2').length).toBeGreaterThan(0)
    // Alice는 PAvatar aria-label + m-name 두 곳 — count로 검증
    expect(screen.getAllByText('Alice').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Bob').length).toBeGreaterThan(0)
  })

  it('canMutate=true: 편집/삭제 버튼 노출', () => {
    render(<TeamDetail team={TEAM} members={MEMBERS} canMutate onEdit={() => {}} onArchive={() => {}} />)
    expect(screen.getByText('편집')).toBeTruthy()
    expect(screen.getByText('삭제')).toBeTruthy()
  })

  it('canMutate=false (AUDITOR): 편집/삭제 버튼 숨김', () => {
    render(
      <TeamDetail
        team={TEAM}
        members={MEMBERS}
        canMutate={false}
        onEdit={() => {}}
        onArchive={() => {}}
        onAddMember={() => {}}
      />,
    )
    expect(screen.queryByText('편집')).toBeNull()
    expect(screen.queryByText('삭제')).toBeNull()
    expect(screen.queryByText('멤버 추가')).toBeNull()
  })

  it('archived 팀: 편집 숨김 + 복원 버튼 노출', () => {
    const archivedTeam = {
      ...TEAM,
      archived: true,
      archivedAt: '2026-05-09T00:00:00Z',
      archivedBy: 'u-1',
    }
    render(
      <TeamDetail
        team={archivedTeam}
        members={MEMBERS}
        canMutate
        onEdit={() => {}}
        onArchive={() => {}}
        onRestore={() => {}}
      />,
    )
    expect(screen.queryByText('편집')).toBeNull()
    expect(screen.queryByText('삭제')).toBeNull()
    expect(screen.getByText('복원')).toBeTruthy()
  })

  it('lead 멤버에 "팀 리더" 서브 표시 + 다른 멤버에 "리더로 지정" 링크', () => {
    render(
      <TeamDetail
        team={TEAM}
        members={MEMBERS}
        canMutate
        onSetLead={() => {}}
        onRemoveMember={() => {}}
      />,
    )
    // "팀 리더"는 stat-label + m-sub 두 곳에 등장
    expect(screen.getAllByText('팀 리더').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('리더로 지정')).toBeTruthy()
  })

  it('"리더로 지정" 클릭 시 onSetLead(userId) 호출', () => {
    const onSetLead = vi.fn()
    render(
      <TeamDetail
        team={TEAM}
        members={MEMBERS}
        canMutate
        onSetLead={onSetLead}
      />,
    )
    fireEvent.click(screen.getByText('리더로 지정'))
    expect(onSetLead).toHaveBeenCalledWith('u-2')
  })

  it('onAddMember 콜백 — 멤버 추가 버튼 클릭 시 호출', () => {
    const onAddMember = vi.fn()
    render(
      <TeamDetail
        team={TEAM}
        members={MEMBERS}
        canMutate
        onAddMember={onAddMember}
      />,
    )
    fireEvent.click(screen.getByText('멤버 추가'))
    expect(onAddMember).toHaveBeenCalled()
  })

  it('편집 버튼 클릭 시 onEdit 호출', () => {
    const onEdit = vi.fn()
    render(
      <TeamDetail
        team={TEAM}
        members={MEMBERS}
        canMutate
        onEdit={onEdit}
      />,
    )
    fireEvent.click(screen.getByText('편집'))
    expect(onEdit).toHaveBeenCalled()
  })
})
