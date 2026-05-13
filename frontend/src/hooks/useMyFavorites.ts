'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * v1.x — 현재 사용자의 즐겨찾기 목록.
 *
 * <p>auth-gated: 미인증 시 401 → onError에서 surface. 본 hook 자체는 retry false로 빠른 fail.
 * 사이드바 pinned row(count)와 `/favorites` 페이지가 같은 캐시 구독.
 *
 * <p>`useToggleStar` mutation의 `invalidations.afterStarToggle`이 본 키를 무효화 → 자동 동기화.
 */
export function useMyFavorites() {
  return useQuery({
    queryKey: qk.myFavorites(),
    queryFn: () => api.listMyFavorites(),
    // 페이지 진입 시 매번 새로고침할 만큼 빈번하지 않음 — 1분 stale.
    staleTime: 60_000,
    retry: false,
  })
}
