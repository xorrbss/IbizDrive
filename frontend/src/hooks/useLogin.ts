'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AuthSession, LoginParams } from '@/types/auth'

/**
 * 로그인 mutation (auth-pages, ADR #41).
 *
 * <p>성공 시 `qk.authMe()` 캐시를 응답으로 직접 셋팅 → 추가 GET /me 불필요.
 * 권한 매트릭스가 user-bound이므로 `qk.all` 전체 무효화 — 새 user의 effective permissions
 * 신선화 (UX 안전망. 백엔드 cacheKey 기반 무효화로 대체될 수 있음).
 *
 * <p>에러는 그대로 propagate — 호출부(LoginPage)가 status/code로 inline 메시지 분기:
 * 401 INVALID_CREDENTIALS, 423 ACCOUNT_LOCKED, 400 VALIDATION_ERROR.
 */
export function useLogin() {
  const qc = useQueryClient()

  return useMutation<AuthSession, Error, LoginParams>({
    mutationFn: (params) => api.login(params),
    onSuccess: async (session) => {
      qc.setQueryData(qk.authMe(), session)
      await qc.invalidateQueries({ queryKey: qk.all })
    },
  })
}
