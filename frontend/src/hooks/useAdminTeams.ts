'use client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  api,
  type AdminTeamDetail,
  type AdminTeamPatch,
  type AdminTeamSummary,
} from '@/lib/api'
import { invalidations, qk } from '@/lib/queryKeys'
import type { TeamCreateRequest, TeamResponse } from '@/types/team'

/**
 * Admin 팀 hooks (admin-teams, T8 design-refresh-admin Phase 4).
 *
 * <p>list 1개 + detail 1개 + mutation 5종(update/archive/restore + create-with-metadata
 * + invite/remove member)을 단일 파일에 모은다 — useAdminDepartments 패턴 답습.
 *
 * <p>모든 mutation 성공 후 {@link invalidations.afterAdminTeamChanged} 또는
 * {@link invalidations.afterTeamMembersChanged}로 캐시 갱신. 옵티미스틱 업데이트는
 * 도입하지 않음 (원칙 #3: 비파괴 외에는 pending 정책).
 */

/**
 * 팀 목록 — 단일 fetch (page 없음). 디자인의 좌측 list panel은 client-side 검색.
 *
 * <p>staleTime=0 (admin은 리얼타임 정확성 우선). 401/403은 retry false로 즉시 노출.
 */
export function useAdminTeams() {
  return useQuery<AdminTeamSummary[]>({
    queryKey: qk.adminTeams.list(),
    queryFn: () => api.adminListTeams(),
    retry: false,
    staleTime: 0,
  })
}

/**
 * 팀 단건 상세. id가 null/undefined면 비활성 (선택 전 상태).
 */
export function useAdminTeam(id: string | null | undefined) {
  return useQuery<AdminTeamDetail>({
    queryKey: id ? qk.adminTeams.detail(id) : ['__skip__'],
    queryFn: () => api.adminGetTeam(id as string),
    enabled: !!id,
    retry: false,
    staleTime: 0,
  })
}

/**
 * 팀 PATCH (name/description/color/leadId).
 *
 * <p>409 TEAM_CONFLICT — rename 시 normalized name 충돌. 호출자(form)가
 * 인라인 메시지로 분기.
 */
export function useAdminUpdateTeam() {
  const qc = useQueryClient()
  return useMutation<AdminTeamDetail, Error, { id: string; body: AdminTeamPatch }>({
    mutationFn: ({ id, body }) => api.adminUpdateTeam(id, body),
    onSuccess: (data) => {
      void invalidations.afterAdminTeamChanged(qc, data.id)
    },
  })
}

/**
 * 팀 archive (DELETE — soft delete).
 */
export function useAdminArchiveTeam() {
  const qc = useQueryClient()
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => api.adminArchiveTeam(id),
    onSuccess: (_data, vars) => {
      void invalidations.afterAdminTeamChanged(qc, vars.id)
    },
  })
}

/**
 * 팀 restore.
 */
export function useAdminRestoreTeam() {
  const qc = useQueryClient()
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => api.adminRestoreTeam(id),
    onSuccess: (_data, vars) => {
      void invalidations.afterAdminTeamChanged(qc, vars.id)
    },
  })
}

/**
 * 디자인 CreateTeamModal — 다단계 합성 mutation:
 * <ol>
 *   <li>POST /api/teams (name, description, visibility=PRIVATE) → 새 team id</li>
 *   <li>PATCH /api/admin/teams/{id} (color, leadId) — leadId가 creator(자동 OWNER)이면 즉시 반영</li>
 *   <li>각 추가 멤버에 대해 POST /api/teams/{id}/members</li>
 * </ol>
 *
 * <p>2단계는 leadId === creator 일 때 backend 멤버 가드 통과를 보장하기 위해 멤버 추가
 * 전에 실행. leadId !== creator인 경우는 디자인 UX상 모달에서 추가한 멤버 중에서만
 * 선택 가능 — 호출 측이 멤버 추가를 모두 끝낸 뒤 leadId를 PATCH로 다시 설정하는 방식이
 * 안전하지만, 본 hook은 KISS로 1차 실행 후 호출자가 leadId 재설정을 별도 mutation으로
 * 보낸다. 본 합성은 lead === creator인 일반 케이스에 최적화.
 */
export interface AdminCreateTeamWithMetadataInput {
  name: string
  description?: string
  color?: string
  /** 추가할 멤버 user id 목록 (creator 본인 제외 — 자동 OWNER로 가입). */
  additionalMemberIds?: string[]
  /** 최종 lead user id. creator면 즉시 PATCH, 아니면 멤버 추가 후 호출자가 별도로 setLead. */
  leadId?: string
  /** leadId가 creator인지 여부 — true면 1단계 직후 PATCH 가능. */
  leadIsCreator?: boolean
}

export function useAdminCreateTeamWithMetadata() {
  const qc = useQueryClient()
  return useMutation<TeamResponse, Error, AdminCreateTeamWithMetadataInput>({
    mutationFn: async (input) => {
      // Step 1: create team (creator becomes auto-OWNER).
      const req: TeamCreateRequest = {
        name: input.name,
        description: input.description,
        visibility: 'PRIVATE',
      }
      const team = await api.createTeam(req)

      // Step 2: additional members (skip if none). Sequential to surface errors clearly.
      for (const userId of input.additionalMemberIds ?? []) {
        await api.inviteTeamMember(team.id, userId)
      }

      // Step 3: PATCH metadata (color always, leadId only when valid).
      const patch: AdminTeamPatch = {}
      if (input.color) patch.color = input.color
      if (input.leadId) patch.leadId = input.leadId
      if (Object.keys(patch).length > 0) {
        await api.adminUpdateTeam(team.id, patch)
      }
      return team
    },
    onSuccess: (team) => {
      void invalidations.afterAdminTeamChanged(qc, team.id)
      void invalidations.afterTeamChanged(qc) // 사이드바 워크스페이스 리스트 갱신
    },
  })
}

/**
 * 팀 멤버 초대 — admin 콘솔 전용 wrapper. 일반 사용자용 {@link useInviteTeamMember}와
 * 동일 endpoint이지만 invalidation 시 admin teams list/detail까지 함께 갱신해
 * memberCount UI 즉시 반영.
 */
export function useAdminInviteTeamMember(teamId: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, { userId: string }>({
    mutationFn: ({ userId }) => api.inviteTeamMember(teamId, userId),
    onSuccess: () => {
      void invalidations.afterTeamMembersChanged(qc, teamId)
      void invalidations.afterAdminTeamChanged(qc, teamId)
    },
  })
}

/**
 * 팀 멤버 제거 — admin wrapper. last-OWNER 가드 시 400 TEAM_OWNER_REQUIRED.
 */
export function useAdminRemoveTeamMember(teamId: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, { userId: string }>({
    mutationFn: ({ userId }) => api.removeTeamMember(teamId, userId),
    onSuccess: () => {
      void invalidations.afterTeamMembersChanged(qc, teamId)
      void invalidations.afterAdminTeamChanged(qc, teamId)
    },
  })
}
