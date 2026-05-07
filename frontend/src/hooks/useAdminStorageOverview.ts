'use client'
import { useQuery } from '@tanstack/react-query'
import { getAdminStorageOverview, type AdminStorageOverviewResponse } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * Admin 스토리지 overview (admin-storage-overview).
 *
 * <p>읽기 전용 단일 페이지 — page/size 인자 없음. mutation 없음 → invalidation 짝 없음.
 * `staleTime` 30초 — 합계는 자주 변하지 않으나 운영자가 새로고침 시 비교적 신선한 값을 원함.
 * 401/403은 `retry: false`로 즉시 노출.
 */
export function useAdminStorageOverview() {
  return useQuery<AdminStorageOverviewResponse>({
    queryKey: qk.adminStorageOverview(),
    queryFn: getAdminStorageOverview,
    retry: false,
    staleTime: 30_000,
  })
}
