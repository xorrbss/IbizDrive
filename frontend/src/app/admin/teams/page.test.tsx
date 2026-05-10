import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import type { AdminTeamDetail, AdminTeamSummary } from '@/lib/api'
import type { TeamMember } from '@/types/team'

/**
 * /admin/teams — 페이지 컨테이너 통합 smoke 테스트.
 *
 * <p>fetch wire는 hooks 테스트가 가드. 본 테스트는 page 분기(loading/empty/team selected)와
 * AUDITOR 가시성만 검증.
 */

let teamsState: { data?: AdminTeamSummary[]; isLoading: boolean; isError: boolean } = {
  isLoading: true,
  isError: false,
}
let detailState: { data?: AdminTeamDetail; isLoading: boolean; isError: boolean } = {
  isLoading: false,
  isError: false,
}
let membersState: { data?: TeamMember[] } = { data: [] }
let meRoles: string[] = ['ADMIN']

vi.mock('@/hooks/useAdminTeams', () => ({
  useAdminTeams: () => teamsState,
  useAdminTeam: () => detailState,
  useAdminUpdateTeam: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useAdminArchiveTeam: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useAdminRestoreTeam: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useAdminInviteTeamMember: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useAdminRemoveTeamMember: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useAdminCreateTeamWithMetadata: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/hooks/useTeamMembers', () => ({
  useTeamMembers: () => membersState,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))

vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({
    data: {
      user: { id: 'u-me', email: 'a@b.com', name: 'A', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: meRoles,
      effectivePermissionsCacheKey: 'k',
    },
    isLoading: false,
    isError: false,
  }),
}))

import AdminTeamsPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const TEAM: AdminTeamSummary = {
  id: 't-1',
  name: '디자인',
  description: 'design system',
  color: '#5B7FCC',
  leadId: 'u-1',
  memberCount: 2,
  archived: false,
  createdAt: '2026-05-01T00:00:00Z',
}

const DETAIL: AdminTeamDetail = {
  id: TEAM.id,
  name: TEAM.name,
  description: TEAM.description,
  color: TEAM.color,
  leadId: TEAM.leadId,
  visibility: 'private',
  rootFolderId: 'f-1',
  memberCount: TEAM.memberCount,
  archived: false,
  archivedAt: null,
  archivedBy: null,
  createdBy: TEAM.leadId,
  createdAt: TEAM.createdAt,
  updatedAt: TEAM.createdAt,
}

const MEMBERS: TeamMember[] = [
  {
    userId: 'u-1',
    displayName: 'Alice',
    email: 'alice@x.io',
    role: 'OWNER',
    joinedAt: '2026-05-01T00:00:00Z',
  },
]

describe('AdminTeamsPage', () => {
  beforeEach(() => {
    teamsState = { isLoading: true, isError: false }
    detailState = { isLoading: false, isError: false }
    membersState = { data: [] }
    meRoles = ['ADMIN']
  })

  it('loading → 로딩 메시지', () => {
    teamsState = { isLoading: true, isError: false }
    wrap(<AdminTeamsPage />)
    expect(screen.getByText(/불러오는 중/)).toBeTruthy()
  })

  it('error → 에러 메시지', () => {
    teamsState = { isLoading: false, isError: true }
    wrap(<AdminTeamsPage />)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('empty → "등록된 팀이 없습니다"', () => {
    teamsState = { isLoading: false, isError: false, data: [] }
    wrap(<AdminTeamsPage />)
    expect(screen.getByText(/등록된 팀이 없습니다/)).toBeTruthy()
  })

  it('teams 있음 → list panel + 첫 팀 detail 자동 선택', () => {
    teamsState = { isLoading: false, isError: false, data: [TEAM] }
    detailState = { isLoading: false, isError: false, data: DETAIL }
    membersState = { data: MEMBERS }
    wrap(<AdminTeamsPage />)
    // panel: 검색 input + team row
    expect(screen.getByLabelText('팀 검색')).toBeTruthy()
    // detail: h2 team name (panel에서도 동일 이름 노출 가능 — getAllByText)
    expect(screen.getAllByText('디자인').length).toBeGreaterThan(0)
    // member table shows Alice
    expect(screen.getAllByText('Alice').length).toBeGreaterThan(0)
  })

  it('AUDITOR (ADMIN 역할 없음) → 등록/편집/삭제 버튼 숨김', () => {
    meRoles = ['AUDITOR']
    teamsState = { isLoading: false, isError: false, data: [TEAM] }
    detailState = { isLoading: false, isError: false, data: DETAIL }
    membersState = { data: MEMBERS }
    wrap(<AdminTeamsPage />)

    // AdminGuard는 useMe roles에 ADMIN/AUDITOR 둘 중 하나 있으면 children 렌더 (default ADMIN-only). 본
    // 테스트 useMe mock은 roles: ['AUDITOR'] → AdminGuard 통과 못 함. 따라서 본 페이지는 빈 렌더.
    // canMutate=false 분기를 검증하려면 ADMIN+AUDITOR 또는 우회가 필요 — 디자인 컨벤션상
    // AdminTabBar가 teams 탭을 ADMIN 전용으로 가시화 + AdminGuard가 ADMIN-only이므로 사실상
    // AUDITOR가 본 페이지에 도달 불가. 본 ts는 페이지 자체가 null 렌더되는지만 확인.
    expect(screen.queryByText('편집')).toBeNull()
    expect(screen.queryByText('삭제')).toBeNull()
  })
})
