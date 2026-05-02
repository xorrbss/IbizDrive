'use client'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api'

/**
 * 토큰 + 새 비밀번호로 비밀번호 재설정 mutation (a1.5).
 *
 * <p>토큰 무효(만료/사용됨/미존재) 시 400 INVALID_TOKEN — 사유는 비공개 (enumeration 방지).
 * 성공 시 백엔드가 모든 세션을 invalidate하므로 사용자는 다시 로그인해야 한다.
 */
export function usePasswordReset() {
  return useMutation<{ message: string }, Error, { token: string; newPassword: string }>({
    mutationFn: ({ token, newPassword }) => api.passwordReset(token, newPassword),
  })
}
