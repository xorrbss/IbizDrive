'use client'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api'

/**
 * 인증 사용자 비밀번호 변경 mutation (a1.5).
 *
 * <p>현재 PW 미일치 시 401 INVALID_CREDENTIALS. 성공 시 다른 기기의 세션은 모두 invalidate되며
 * 현재 세션은 유지 — caller는 router.replace 등 추가 redirect 불필요.
 */
export function usePasswordChange() {
  return useMutation<{ message: string }, Error, { currentPassword: string; newPassword: string }>({
    mutationFn: ({ currentPassword, newPassword }) =>
      api.passwordChange(currentPassword, newPassword),
  })
}
