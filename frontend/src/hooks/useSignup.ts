'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AuthSession, SignupParams } from '@/types/auth'

/**
 * 회원가입 mutation (auth-pages, ADR #41 — supersedes ADR #18).
 *
 * <p>backend가 응답에 자동 로그인 세션을 발급하므로 useLogin과 동일하게 `qk.authMe()` 캐시 셋팅 +
 * 전체 무효화. 사용자는 이어서 `/files`로 redirect되어 즉시 사용.
 *
 * <p>에러:
 * <ul>
 *   <li>409 CONFLICT/DUPLICATE_EMAIL — 이미 가입된 이메일</li>
 *   <li>400 VALIDATION_ERROR — password ADR #19 규칙 위반(12자 이상·영문+숫자·공백 금지), 잘못된 email, blank displayName</li>
 * </ul>
 * 호출부(SignupPage)가 status/code/details로 분기.
 */
export function useSignup() {
  const qc = useQueryClient()

  return useMutation<AuthSession, Error, SignupParams>({
    mutationFn: (params) => api.signup(params),
    onSuccess: async (session) => {
      qc.setQueryData(qk.authMe(), session)
      await qc.invalidateQueries({ queryKey: qk.all })
    },
  })
}
