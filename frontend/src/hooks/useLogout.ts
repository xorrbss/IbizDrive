'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * 로그아웃 mutation (auth-pages, ADR #41).
 *
 * <p>성공 시:
 * <ol>
 *   <li>`qk.authMe()` 캐시를 null로 셋팅 (즉시 사이드바/가드가 비로그인 인식)</li>
 *   <li>전체 캐시 clear — 다른 user 로그인 시 이전 권한/데이터 잔재 차단</li>
 * </ol>
 *
 * <p>네트워크 401/5xx 시에도 사용자 의도는 "로그아웃"이므로 caller(사이드바)는 catch 후
 * 캐시 clear + redirect를 강제 — `useLogout().mutateAsync().catch()` 패턴.
 */
export function useLogout() {
  const qc = useQueryClient()

  return useMutation<void, Error, void>({
    mutationFn: () => api.logout(),
    onSettled: () => {
      // 성공/실패 무관 — 사용자 의도가 로그아웃이므로 캐시는 비운다.
      qc.setQueryData(qk.authMe(), null)
      qc.clear()
    },
  })
}
