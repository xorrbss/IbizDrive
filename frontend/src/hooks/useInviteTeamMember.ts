'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/** Plan F T10 — 팀에 사용자 초대. */
export function useInviteTeamMember(teamId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId }: { userId: string }) => api.inviteTeamMember(teamId, userId),
    onSuccess: () => invalidations.afterTeamMembersChanged(qc, teamId),
  })
}
