import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { PermissionListItem } from '@/types/permission'

/**
 * `useResourcePermissions(resourceType, id, opts?)` — 리소스에 부여된 grant 목록 (M8.1).
 *
 * BE `GET /api/{folders|files}/:id/permissions` (docs/02 §7.10) 호출. PERMISSION_ADMIN
 * 전용이므로 호출자(예: PermissionsTab) 측에서 `usePermission(id).PERMISSION_ADMIN` 으로
 * `enabled` 게이팅 — 미보유자에게는 fetch 자체가 발생하지 않음.
 *
 * staleTime 30s — audit 류와 동일 가정. grant/revoke mutation 후 invalidate 트랙은 후속 PR.
 *
 * 반환은 `UseQueryResult` 그대로 — 호출 컴포넌트가 4상태 (loading/error/empty/data) 분기를
 * 직접 처리 (A16 SharesTable 동형). hook 자체는 normalize 안 함.
 */
export function useResourcePermissions(
  resourceType: 'folder' | 'file',
  id: string,
  opts?: { enabled?: boolean },
): UseQueryResult<PermissionListItem[], Error> {
  return useQuery({
    queryKey: qk.resourcePermissions(resourceType, id),
    queryFn: () => api.listResourcePermissions(resourceType, id),
    staleTime: 30_000,
    enabled: opts?.enabled ?? true,
  })
}
