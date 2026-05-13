'use client'
import { useQuery } from '@tanstack/react-query'
import { listAdminApprovals } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { useMe } from '@/hooks/useMe'
import type { AdminApprovalPage } from '@/types/admin-approval'

/**
 * dual-approval framework Phase 4 — AdminTabBar `승인` 탭 pending count 배지용
 * 가벼운 카운트 hook (ADR #47, docs/02 §2.11).
 *
 * <p>`GET /api/admin/approvals?size=1` 만 호출 — backend는 status='REQUESTED' 만
 * 반환하므로 page meta `totalElements`가 곧 pending 합계. content body는 1행만
 * 받으므로 wire/parse 비용 최소.
 *
 * <p>ROLE 가드: ADMIN 보유자만 호출. 비-ADMIN(AUDITOR/MEMBER)은 backend가 403을
 * 반환하므로 `enabled: false`로 사전 차단 — 불필요 noise 회피. 결과 미존재 시
 * count=0 으로 자연스럽게 배지 미렌더.
 *
 * <p>staleTime 60_000 (1분, KISS) — 빠른 polling 회피. 새 approval 생성/decision
 * 후 mutation hook의 `invalidations.afterAdminApprovalDecided`가
 * `qk.adminApprovals()` prefix 무효화하므로 본 키도 같이 갱신된다.
 */
export function useAdminPendingApprovalsCount(): {
  count: number
  isLoading: boolean
  isError: boolean
} {
  const { data: me } = useMe()
  const isAdmin = (me?.roles ?? []).includes('ADMIN')

  const query = useQuery<AdminApprovalPage>({
    queryKey: qk.adminApprovalsList({ size: 1, page: 0 }),
    queryFn: () => listAdminApprovals({ size: 1, page: 0 }),
    enabled: isAdmin,
    retry: false,
    staleTime: 60_000,
  })

  return {
    count: query.data?.totalElements ?? 0,
    isLoading: isAdmin ? query.isLoading : false,
    isError: query.isError,
  }
}
