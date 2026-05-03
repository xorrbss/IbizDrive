'use client'
import { useMutation } from '@tanstack/react-query'
import { api, type AdminInviteUserParams, type AdminInvitedUser } from '@/lib/api'

/**
 * Admin invite mutation (m-admin-entry-rewrite P7).
 *
 * <p>backend `POST /api/admin/users`로 신규 user 생성 + 임시 PW 이메일 발송. 응답에는 임시 PW
 * 미포함 (docs/03 §2.8). 사용자 목록 캐시는 본 트랙에서 도입하지 않으므로 invalidate 없음.
 *
 * <p>에러 분기 (호출부 책임):
 * <ul>
 *   <li>400 VALIDATION_ERROR — 입력 검증 실패 (form 인라인 메시지)</li>
 *   <li>403 — 권한 없음 (UX 가드 우회 케이스 — 일반적으로 도달 불가)</li>
 *   <li>409 CONFLICT/DUPLICATE_EMAIL — 동일 email 존재 (form 인라인 메시지)</li>
 * </ul>
 */
export function useAdminInviteUser() {
  return useMutation<AdminInvitedUser, Error, AdminInviteUserParams>({
    mutationFn: (params) => api.adminInviteUser(params),
  })
}
