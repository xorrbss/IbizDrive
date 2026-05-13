'use client'
import { useQuery } from '@tanstack/react-query'
import { getAdminApproval } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AdminApprovalDto } from '@/types/admin-approval'

/**
 * dual-approval framework Phase 4 — 단건 상세 hook (ADR #47).
 *
 * <p>현 MVP UI는 list만 사용하지만 detail은 향후 expand row / drill-down 진입점으로
 * 예약 — keyspace 분리로 list/detail 동시 무효화도 prefix 매칭으로 처리 가능
 * ({@code invalidations.afterAdminApprovalDecided}).
 *
 * <p>{@code id}가 falsy면 query 비활성 — 옵셔널 호출 패턴 지원.
 */
export function useAdminApproval(id: string | undefined | null) {
  return useQuery<AdminApprovalDto>({
    queryKey: qk.adminApproval(id ?? ''),
    queryFn: () => getAdminApproval(id as string),
    enabled: !!id,
    retry: false,
  })
}
