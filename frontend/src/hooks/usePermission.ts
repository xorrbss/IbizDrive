import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { PERMISSIONS, type Permission } from '@/types/permission'

/**
 * `usePermission(nodeId?)` — 노드/전역 effective 권한 (M8 docs/01 §14.2).
 *
 * - 반환: `Record<Permission, boolean>` (UPPER_SNAKE_CASE — types/permission.ts 미러)
 * - 사용처: UX 게이트(메뉴 disable/숨김) 한정 — 보안 boundary 아님 (CLAUDE.md §3 원칙 10)
 * - 로딩 중에는 모든 플래그 false → "권한 없음" 보수적 디폴트로 깜빡임 방지
 * - staleTime 60s — 서버 측 권한 갱신 빈도 가정 (docs/01 §14.2)
 *
 * 백엔드 endpoint 신설 시 `api.getEffectivePermissions` 내부만 교체.
 */
export type PermissionFlags = Record<Permission, boolean>

const NONE: PermissionFlags = Object.freeze(
  PERMISSIONS.reduce((acc, p) => {
    acc[p] = false
    return acc
  }, {} as PermissionFlags),
)

export function usePermission(nodeId?: string): PermissionFlags {
  const { data } = useQuery({
    queryKey: qk.permissions(nodeId),
    queryFn: () => api.getEffectivePermissions(nodeId),
    staleTime: 60_000,
  })
  if (!data) return NONE
  const flags = { ...NONE }
  for (const p of data) flags[p] = true
  return flags
}
