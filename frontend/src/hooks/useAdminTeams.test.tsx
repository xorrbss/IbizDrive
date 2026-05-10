import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import {
  useAdminArchiveTeam,
  useAdminCreateTeamWithMetadata,
  useAdminInviteTeamMember,
  useAdminRemoveTeamMember,
  useAdminRestoreTeam,
  useAdminTeam,
  useAdminTeams,
  useAdminUpdateTeam,
} from './useAdminTeams'
import { api, type AdminTeamDetail, type AdminTeamSummary } from '@/lib/api'
import type { TeamResponse } from '@/types/team'

vi.mock('@/lib/api', () => ({
  api: {
    adminListTeams: vi.fn(),
    adminGetTeam: vi.fn(),
    adminUpdateTeam: vi.fn(),
    adminArchiveTeam: vi.fn(),
    adminRestoreTeam: vi.fn(),
    createTeam: vi.fn(),
    inviteTeamMember: vi.fn(),
    removeTeamMember: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const SUMMARY: AdminTeamSummary = {
  id: 't1111111-1111-1111-1111-111111111111',
  name: '디자인',
  description: '디자인 시스템 팀',
  color: '#5B7FCC',
  leadId: 'u1111111-1111-1111-1111-111111111111',
  memberCount: 3,
  archived: false,
  createdAt: '2026-05-01T00:00:00Z',
}

const DETAIL: AdminTeamDetail = {
  id: SUMMARY.id,
  name: SUMMARY.name,
  description: SUMMARY.description,
  color: SUMMARY.color,
  leadId: SUMMARY.leadId,
  visibility: 'private',
  rootFolderId: 'f1111111-1111-1111-1111-111111111111',
  memberCount: SUMMARY.memberCount,
  archived: false,
  archivedAt: null,
  archivedBy: null,
  createdBy: SUMMARY.leadId,
  createdAt: SUMMARY.createdAt,
  updatedAt: SUMMARY.createdAt,
}

const TEAM_RESPONSE: TeamResponse = {
  id: SUMMARY.id,
  name: SUMMARY.name,
  description: SUMMARY.description,
  visibility: 'PRIVATE',
  rootFolderId: 'f1111111-1111-1111-1111-111111111111',
  createdAt: SUMMARY.createdAt,
  archivedAt: null,
}

describe('useAdminTeams', () => {
  beforeEach(() => vi.clearAllMocks())

  it('list 호출 + 결과 반환', async () => {
    ;(api.adminListTeams as ReturnType<typeof vi.fn>).mockResolvedValue([SUMMARY])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminTeams(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminListTeams).toHaveBeenCalledTimes(1)
    expect(result.current.data).toEqual([SUMMARY])
  })

  it('403 → isError + retry 비활성', async () => {
    const err = Object.assign(new Error('failed'), { status: 403 })
    ;(api.adminListTeams as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminTeams(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(api.adminListTeams).toHaveBeenCalledTimes(1)
  })
})

describe('useAdminTeam', () => {
  beforeEach(() => vi.clearAllMocks())

  it('id null이면 fetch 안 함', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAdminTeam(null), { wrapper: wrap(qc) })
    expect(api.adminGetTeam).not.toHaveBeenCalled()
  })

  it('id 주어지면 detail fetch', async () => {
    ;(api.adminGetTeam as ReturnType<typeof vi.fn>).mockResolvedValue(DETAIL)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminTeam(SUMMARY.id), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminGetTeam).toHaveBeenCalledWith(SUMMARY.id)
    expect(result.current.data).toEqual(DETAIL)
  })
})

describe('useAdminUpdateTeam', () => {
  beforeEach(() => vi.clearAllMocks())

  it('PATCH + invalidate', async () => {
    ;(api.adminUpdateTeam as ReturnType<typeof vi.fn>).mockResolvedValue(DETAIL)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useAdminUpdateTeam(), { wrapper: wrap(qc) })

    await result.current.mutateAsync({
      id: SUMMARY.id,
      body: { color: '#C16A8B' },
    })
    expect(api.adminUpdateTeam).toHaveBeenCalledWith(SUMMARY.id, {
      color: '#C16A8B',
    })
    // adminTeams.list + adminTeams.detail 무효화 발생
    expect(invalidateSpy).toHaveBeenCalled()
  })

  it('409 TEAM_CONFLICT는 에러 surface', async () => {
    const err = Object.assign(new Error('conflict'), {
      status: 409,
      code: 'TEAM_CONFLICT',
    })
    ;(api.adminUpdateTeam as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminUpdateTeam(), { wrapper: wrap(qc) })

    await expect(
      result.current.mutateAsync({ id: SUMMARY.id, body: { name: 'dup' } }),
    ).rejects.toMatchObject({ status: 409, code: 'TEAM_CONFLICT' })
  })
})

describe('useAdminArchiveTeam / useAdminRestoreTeam', () => {
  beforeEach(() => vi.clearAllMocks())

  it('archive — DELETE 위임', async () => {
    ;(api.adminArchiveTeam as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminArchiveTeam(), { wrapper: wrap(qc) })

    await result.current.mutateAsync({ id: SUMMARY.id })
    expect(api.adminArchiveTeam).toHaveBeenCalledWith(SUMMARY.id)
  })

  it('restore — POST /restore 위임', async () => {
    ;(api.adminRestoreTeam as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminRestoreTeam(), { wrapper: wrap(qc) })

    await result.current.mutateAsync({ id: SUMMARY.id })
    expect(api.adminRestoreTeam).toHaveBeenCalledWith(SUMMARY.id)
  })
})

describe('useAdminCreateTeamWithMetadata', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lead === creator + 멤버 추가 — createTeam → invite x N → patch metadata', async () => {
    ;(api.createTeam as ReturnType<typeof vi.fn>).mockResolvedValue(TEAM_RESPONSE)
    ;(api.inviteTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    ;(api.adminUpdateTeam as ReturnType<typeof vi.fn>).mockResolvedValue(DETAIL)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminCreateTeamWithMetadata(), {
      wrapper: wrap(qc),
    })

    const res = await result.current.mutateAsync({
      name: '디자인',
      description: '디자인 시스템 팀',
      color: '#5B7FCC',
      additionalMemberIds: ['u-2', 'u-3'],
      leadIsCreator: true,
    })
    expect(res).toEqual(TEAM_RESPONSE)
    expect(api.createTeam).toHaveBeenCalledWith({
      name: '디자인',
      description: '디자인 시스템 팀',
      visibility: 'PRIVATE',
    })
    expect(api.inviteTeamMember).toHaveBeenCalledTimes(2)
    expect(api.inviteTeamMember).toHaveBeenNthCalledWith(1, TEAM_RESPONSE.id, 'u-2')
    expect(api.inviteTeamMember).toHaveBeenNthCalledWith(2, TEAM_RESPONSE.id, 'u-3')
    expect(api.adminUpdateTeam).toHaveBeenCalledWith(TEAM_RESPONSE.id, {
      color: '#5B7FCC',
    })
  })

  it('color/leadId 모두 미지정 — PATCH 생략', async () => {
    ;(api.createTeam as ReturnType<typeof vi.fn>).mockResolvedValue(TEAM_RESPONSE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminCreateTeamWithMetadata(), {
      wrapper: wrap(qc),
    })

    await result.current.mutateAsync({
      name: '미니',
      additionalMemberIds: [],
      leadIsCreator: true,
    })
    expect(api.createTeam).toHaveBeenCalled()
    expect(api.adminUpdateTeam).not.toHaveBeenCalled()
  })
})

describe('useAdminInviteTeamMember / useAdminRemoveTeamMember', () => {
  beforeEach(() => vi.clearAllMocks())

  it('invite — POST /api/teams/{id}/members 위임', async () => {
    ;(api.inviteTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminInviteTeamMember(SUMMARY.id), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({ userId: 'u-9' })
    expect(api.inviteTeamMember).toHaveBeenCalledWith(SUMMARY.id, 'u-9')
  })

  it('remove — DELETE /api/teams/{id}/members/{uid} 위임', async () => {
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminRemoveTeamMember(SUMMARY.id), {
      wrapper: wrap(qc),
    })
    await result.current.mutateAsync({ userId: 'u-9' })
    expect(api.removeTeamMember).toHaveBeenCalledWith(SUMMARY.id, 'u-9')
  })
})
