'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AdminDashboardSummary } from '@/types/admin'

/**
 * Admin 대시보드 KPI (admin-dashboard 트랙).
 *
 * <p>backend `GET /api/admin/dashboard/summary` 응답에서 envelope을 벗겨 {@link AdminDashboardSummary}
 * 단일 객체로 노출. 호출자는 `data.users.total`처럼 직접 접근.
 *
 * <p>{@code staleTime} 0 — admin 화면은 리얼타임 정확성 우선. {@code retry} false — 401/403은
 * 즉시 에러 노출 (admin 페이지 무한 retry 방지). useAdminUsers 정책 mirror.
 */
export function useAdminDashboardSummary() {
  return useQuery<AdminDashboardSummary>({
    queryKey: qk.adminDashboard(),
    queryFn: async () => (await api.adminGetDashboardSummary()).summary,
    retry: false,
    staleTime: 0,
  })
}
