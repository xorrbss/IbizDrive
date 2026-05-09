'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'

/**
 * 현재 폴더 detail. URL 파생만 — Plan B로 workspace prefix 합쳐도 시그니처 유지.
 *
 * - workspace 컨텍스트 안: folderId = parseWorkspaceUrl(pathname).folderId.
 *   folderId가 null(workspace landing /d/:id)이면 useWorkspaces로 root를 별도 조회한 페이지에서
 *   redirect 처리(Task 8/9). 본 훅은 folderId가 있을 때만 enabled.
 * - workspace 외 (/admin, /login …): folderId = '', enabled=false → no-op.
 */
export function useCurrentFolder() {
  const ws = useCurrentWorkspace()
  const folderId = ws?.folderId ?? ''
  const { data, isLoading, error } = useQuery({
    queryKey: qk.folder(folderId),
    queryFn: () => api.getFolder(folderId),
    enabled: folderId.length > 0,
    staleTime: 60_000,
  })
  return {
    folderId,
    folder: data,
    breadcrumb: data?.breadcrumb ?? [],
    isLoading,
    error,
  }
}
