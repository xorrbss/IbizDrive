'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AuthSession } from '@/types/auth'

/**
 * 현재 로그인 사용자(`/api/auth/me`) 조회 (auth-pages, ADR #41).
 *
 * <p>401(미인증)을 정상 결과(null)로 매핑하여 (explorer) layout 401 guard와 비로그인 페이지가
 * 동일 hook으로 분기 가능하게 한다. 다른 status(5xx)는 throw → ErrorBoundary 또는 호출부 처리.
 *
 * <p>retry false — 401은 재시도해도 결과 동일 + 사용자가 잠시 비로그인 상태인 게 정상이므로 noise 회피.
 * staleTime 1분 — 같은 페이지 내 여러 컴포넌트가 useMe()를 호출해도 1회만 fetch.
 */
export function useMe() {
  return useQuery<AuthSession | null>({
    queryKey: qk.authMe(),
    queryFn: async () => {
      try {
        return await api.me()
      } catch (e) {
        const err = e as Error & { status?: number }
        if (err.status === 401) return null
        throw e
      }
    },
    retry: false,
    staleTime: 60_000,
  })
}
