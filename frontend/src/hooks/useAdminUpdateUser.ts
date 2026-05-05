'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type AdminUserPatchBody, type AdminUserSummary } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/**
 * Admin 사용자 PATCH mutation — admin-user-mgmt.
 *
 * <p>backend `PATCH /api/admin/users/:id` 호출 후 admin 사용자 목록 prefix 무효화. 본 트랙은
 * 옵티미스틱 업데이트 미적용 — role/active 변경은 self-protection 검증이 서버에서만 가능하므로
 * 서버 응답을 기다린 뒤 스냅샷으로 갱신하는 보수적 정책 (원칙 #3 비파괴 외에는 pending).
 *
 * <p>호출자가 status/code로 에러 분기 (호출부 책임):
 * <ul>
 *   <li>400 VALIDATION_ERROR — body 빈 객체 / 미지원 isActive=true</li>
 *   <li>403 SELF_PROTECTION — actor==target self-demote/self-deactivate</li>
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
