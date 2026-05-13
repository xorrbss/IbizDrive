'use client'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { listAdminApprovals } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type {
  AdminApprovalFilters,
  AdminApprovalPage,
} from '@/types/admin-approval'

/**
 * dual-approval framework Phase 4 — `/api/admin/approvals` 목록 hook (ADR #47).
 *
 * <p>read-only — invalidations은 mutation hook이 담당. {@code actionType}이 없으면
 * 모든 pending 합산. page/size 기본값은 backend default (0/50) — 호출 측이 명시한
 * 값이 우선.
 *
 * <p>page 전환 깜빡임 완화: keepPreviousData (TanStack Query v5 placeholderData 함수).
 */
export function useAdminApprovals(filters: AdminApprovalFilters = {}) {
  const normalized: AdminApprovalFilters = {
    actionType: filters.actionType,
    page: filters.page ?? 0,
    size: filters.size ?? 50,
  }
  return useQuery<AdminApprovalPage>({
    queryKey: qk.adminApprovalsList(normalized),
    queryFn: () => listAdminApprovals(normalized),
    retry: false,
    staleTime: 0,
    placeholderData: keepPreviousData,
  })
}
