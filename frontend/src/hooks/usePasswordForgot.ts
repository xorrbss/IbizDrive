'use client'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api'

/**
 * 비밀번호 재설정 메일 요청 mutation (a1.5).
 *
 * <p>anti-enumeration — 백엔드는 가입/미가입 무관 200 동일 응답. 호출부는 항상 같은 안내
 * 메시지를 노출 ("가입된 이메일이라면 발송됩니다").
 *
 * <p>네트워크/5xx만 실패로 표면화. 400 VALIDATION_ERROR (blank email)도 caller에서 inline 메시지.
 */
export function usePasswordForgot() {
  return useMutation<{ message: string }, Error, { email: string }>({
    mutationFn: ({ email }) => api.passwordForgot(email),
  })
}
