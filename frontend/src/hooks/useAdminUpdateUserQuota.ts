'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/**
 * Admin 사용자 quota PUT mutation — quota mutation Phase 4 (`docs/04 §6.1`).
 *
 * <p>backend `PUT /api/admin/users/:id/quota` (#198 Phase 3 endpoint) 호출 후 admin 사용자 목록
 * prefix 무효화 — 캐시 row의 {@code storageQuota}/{@code storageUsed}가 재조회로 갱신.
 *
 * <p>옵티미스틱 업데이트 미적용 (CLAUDE.md §3 원칙 3 — quota 변경은 destructive scope에 가까움,
 * pending 로딩 상태로 처리). 동일 값 호출은 backend가 no-op으로 처리하므로 별도 short-circuit 불필요.
 *
 * <p>호출자가 status/code로 에러 분기:
 * <ul>
 *   <li>400 VALIDATION_ERROR — storageQuota 음수 / null / 누락</li>
 *   <li>403 — ROLE_ADMIN 부재</li>
 *   <li>404 USER_NOT_FOUND — target 미존재/soft-deleted</li>
 * </ul>
 */
export function useAdminUpdateUserQuota() {
  const qc = useQueryClient()
  return useMutation<
    { storageQuota: number; storageUsed: number },
    Error,
    { id: string; storageQuota: number }
  >({
    mutationFn: ({ id, storageQuota }) => api.adminUpdateUserQuota(id, storageQuota),
    onSuccess: () => {
      void invalidations.afterAdminUserChanged(qc)
    },
  })
}
