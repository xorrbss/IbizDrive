'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { GrantPermissionRequest, PermissionListItem } from '@/types/permission'

/**
 * Resource-level 권한 grant mutation — grant-permission-dialog Phase B (docs/01 §14.5).
 *
 * <p>Phase B 한정 — `subject = 'everyone'` 만 dialog UI에서 허용 (Phase C에서 USER/DEPT/ROLE 분기).
 * 본 훅 자체는 4종 subject 모두 송신 가능하므로 wire 레벨에서 제한이 없다.
 *
 * <p>성공 시 invalidations.afterPermissionGrant로 3종 prefix 무효화 (queryKeys.ts §6.1):
 * resourcePermissions / adminPermissions / permissions(자기 effective).
 *
 * <p>낙관적 업데이트 미적용 — 권한 변경은 §3 원칙 3 (파괴적 액션은 pending 로딩 상태) 정합.
 * onError는 호출자가 mutation.error로 받아 status별 분기 (409 inline / 403,404 toast+close / 400 field).
 */
type Vars = {
  resource: 'folder' | 'file'
  resourceId: string
  body: GrantPermissionRequest
}

export function useGrantPermission() {
  const qc = useQueryClient()
  return useMutation<PermissionListItem, Error, Vars>({
    mutationFn: ({ resource, resourceId, body }) =>
      api.grantPermission(resource, resourceId, body),
    onSuccess: (_data, vars) =>
      invalidations.afterPermissionGrant(qc, vars.resource, vars.resourceId),
  })
}
