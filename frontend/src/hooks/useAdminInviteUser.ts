'use client'
import { useMutation } from '@tanstack/react-query'
import { api, type AdminInviteUserParams, type AdminInviteUserResponse } from '@/lib/api'

/**
 * ADR #21 admin closure (P3) — admin이 신규 사용자를 초대하는 mutation.
 *
 * <p>단순 위임형 hook: 캐시 무효화/store 갱신은 하지 않는다. 사용자 목록 화면이 본 트랙에 없고,
 * 페이지(`/admin/users`)는 onSuccess에서 폼 reset + 안내 토스트만 노출하면 충분하다.
 *
 * <p>오류는 호출 측이 `error.status` / `error.code` / `error.reason`로 분기한다 — 409
 * DUPLICATE_EMAIL, 403 PERMISSION_DENIED, 400 VALIDATION_ERROR.
 */
export function useAdminInviteUser() {
  return useMutation<AdminInviteUserResponse, Error, AdminInviteUserParams>({
    mutationFn: (params) => api.adminInviteUser(params),
  })
}
