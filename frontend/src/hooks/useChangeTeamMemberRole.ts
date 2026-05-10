'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { TeamMemberRole } from '@/types/team'

/** Plan F T12 — 팀 멤버 역할 변경. last-OWNER 강등 시도 시 400 TEAM_OWNER_REQUIRED. */
export function useChangeTeamMemberRole(teamId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: TeamMemberRole }) =>
      api.changeTeamMemberRole(teamId, userId, role),
    onSuccess: () => invalidations.afterTeamMembersChanged(qc, teamId),
  })
}
