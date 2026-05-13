'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * v1.x User Home Dashboard SharedWithMeCard — 사용자 본인이 USER subject 로 직접 받은 active grant.
 *
 * <p>backend {@code GET /api/me/shared-with-me?limit=N}. auth-gated: 미인증 시 401 → onError 에서 surface.
 *
 * <p>`useToggleStar` mutation 의 invalidations 와 무관 — permission grant/revoke mutation 에서
 * {@link qk.mySharedWithMe} 를 invalidate 하도록 후속 트랙에서 wiring.
 */
export function useMySharedWithMe(limit = 5) {
  return useQuery({
    queryKey: qk.mySharedWithMe(),
    queryFn: () => api.fetchMySharedWithMe(limit),
    staleTime: 60_000,
    retry: false,
  })
}
