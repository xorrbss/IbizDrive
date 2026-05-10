'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * Plan F T9 — 팀 멤버 목록 조회.
 * `teamId === null` 이면 비활성 (예: 라우트 미진입). 캐시 키는 `qk.teams.members(teamId)`.
 */
export function useTeamMembers(teamId: string | null) {
  return useQuery({
    queryKey: teamId ? qk.teams.members(teamId) : ['__skip__'],
    queryFn: () => api.getTeamMembers(teamId!),
    enabled: !!teamId,
  })
}
