'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * 사용자의 workspace 목록 — 사이드바 + 라우팅의 single source.
 * staleTime 60초 — Plan B에서는 user의 부서/팀 멤버십이 거의 변하지 않음.
 * gcTime 10분 — 라우트 전환에서 사이드바 reflesh 비용 최소화.
 */
export function useWorkspaces() {
  return useQuery({
    queryKey: qk.workspaces.me(),
    queryFn: api.getWorkspacesMe,
    staleTime: 60_000,
    gcTime: 10 * 60_000,
  })
}
