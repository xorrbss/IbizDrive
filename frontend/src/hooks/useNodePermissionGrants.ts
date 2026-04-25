// frontend/src/hooks/useNodePermissionGrants.ts
// RightPanel 권한 탭용 — 노드에 부여된 사용자/그룹 grant 목록.

'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

export function useNodePermissionGrants(nodeId: string | null) {
  return useQuery({
    queryKey: nodeId ? qk.nodePermissionGrants(nodeId) : ['__permissions_grants_disabled__'],
    queryFn: () => api.getNodePermissionGrants(nodeId!),
    enabled: !!nodeId,
    staleTime: 30_000,
  })
}
