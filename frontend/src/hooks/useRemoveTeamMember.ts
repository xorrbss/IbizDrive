'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/** Plan F T11 — 팀에서 멤버 제거. self-remove 처리는 호출자가 router.push로 분기. */
export function useRemoveTeamMember(teamId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId }: { userId: string }) => api.removeTeamMember(teamId, userId),
    onSuccess: () => invalidations.afterTeamMembersChanged(qc, teamId),
  })
}
