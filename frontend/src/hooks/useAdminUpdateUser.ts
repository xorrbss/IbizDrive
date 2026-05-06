'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type AdminUserPatchBody, type AdminUserSummary } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/**
 * Admin 사용자 PATCH mutation — admin-user-mgmt + admin-user-search-update.
 *
 * <p>backend `PATCH /api/admin/users/:id` 호출 후 admin 사용자 목록 prefix 무효화. 본 훅은
 * role/isActive(true|false)/displayName 모든 변종을 동일 mutate로 처리 — body 형태가 그대로
 * backend AdminUserPatchRequest로 직렬화. 옵티미스틱 업데이트는 미적용(원칙 #3).
 *
 * <p>호출자가 status/code로 에러 분기 (호출부 책임):
 * <ul>
 *   <li>400 VALIDATION_ERROR — body 빈 객체 / displayName blank 또는 길이 초과</li>
 *   <li>403 SELF_PROTECTION — actor==target self-demote/self-deactivate (reactivate/displayName은 self 허용)</li>
 *   <li>404 USER_NOT_FOUND — target 미존재</li>
 * </ul>
 */
export function useAdminUpdateUser() {
  const qc = useQueryClient()
  return useMutation<
    AdminUserSummary,
    Error,
    { id: string; body: AdminUserPatchBody }
  >({
    mutationFn: ({ id, body }) => api.adminUpdateUser(id, body),
    onSuccess: () => {
      void invalidations.afterAdminUserChanged(qc)
    },
  })
}
