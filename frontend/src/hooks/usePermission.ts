// frontend/src/hooks/usePermission.ts
// 설계: docs/01 §14.2 (권한 훅)
//
// 프론트 권한은 UX용 — 백엔드 권한 검증이 보안의 마지막 방어선 (CLAUDE.md §3 원칙 10).
// 403 응답은 일급 에러 (M3에서 전역 처리, §11).

'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { Permission, PermissionFlags } from '@/types/permission'

const FALLBACK: PermissionFlags = {
  read: false,
  upload: false,
  edit: false,
  delete: false,
  download: false,
  move: false,
  share: false,
  admin: false,
}

/**
 * 노드별 effective permissions를 PermissionFlags로 변환해 반환.
 *
 * - nodeId 미지정 시 전역(루트) 권한 — 사이드바 "새 폴더" 등에 사용.
 * - 로딩 중에는 모든 권한 false (FALLBACK). UI는 disabled 상태로 안전하게 시작.
 * - staleTime 60초 — 권한 변경은 드물고, 변경 시 백엔드가 cache invalidation 신호.
 */
export function usePermission(nodeId?: string): PermissionFlags {
  const { data } = useQuery({
    queryKey: qk.effectivePermissions(nodeId),
    queryFn: () => api.getEffectivePermissions(nodeId),
    staleTime: 60_000,
  })
  if (!data) return FALLBACK
  const set = new Set<Permission>(data)
  return {
    read: set.has('read'),
    upload: set.has('upload'),
    edit: set.has('edit'),
    delete: set.has('delete'),
    download: set.has('download'),
    move: set.has('move'),
    share: set.has('share'),
    admin: set.has('admin'),
  }
}

export type { Permission, PermissionFlags } from '@/types/permission'
