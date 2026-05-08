'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * Admin 권한 revoke mutation — admin-permission-revoke (Wave 2 T5 follow-up).
 *
 * <p>성공 시 `qk.adminPermissions()` 전체를 invalidate하여 활성 list 모두 재조회.
 * mutate 단위는 단일 row id만 받는다 (backend `DELETE /api/permissions/{id}`).
 *
 * <p>낙관적 업데이트 미적용 — 권한 변경은 §3 원칙 3 (파괴적 액션은 pending 로딩 상태)
 * 정합. UI는 mutation pending 동안 버튼 disabled.
 */
export function useAdminRevokePermission() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: (permissionId) => api.adminRevokePermission(permissionId),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.adminPermissions() })
    },
  })
}
