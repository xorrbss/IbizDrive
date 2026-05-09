'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * 사이드바 lazy children 조회 — spec §4.5 §3.
 * `enabled`가 false면 호출 안 함 (접힌 폴더는 fetch X).
 *
 * staleTime 30초: 다른 세션 mutation을 너무 늦게 반영하지 않도록 짧게.
 * gcTime 5분: 트리 접었다 펼치기 직후 재페치 방지.
 */
export function useFolderChildren(
  scopeType: 'department' | 'team',
  scopeId: string,
  parentId: string,
  opts: { enabled: boolean },
) {
  return useQuery({
    queryKey: qk.folderChildren(scopeType, scopeId, parentId),
    queryFn: () => api.getFolderChildren(parentId),
    enabled: opts.enabled,
    staleTime: 30_000,
    gcTime: 5 * 60_000,
  })
}
