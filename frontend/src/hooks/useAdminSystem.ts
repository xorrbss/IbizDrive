'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { CronJobsResponse } from '@/types/system'

/**
 * Wave 1 — T3 — `/admin/system` cron 설정 스냅샷 단일 조회 hook.
 *
 * <p>read-only이므로 mutation 없음. staleTime=30s — 4 cron 설정은 파일 기반(application.yml)
 * + 재기동 시점에만 변경되므로 잦은 refetch 비용을 줄인다 (admin-department-crud의 staleTime=0
 * 정책과 다른 이유 — 그 트랙은 사용자 mutation으로 즉시 변하지만 본 트랙 데이터는 정적).
 *
 * <p>retry=false — 401/403는 즉시 표면화 (가드 누락 회귀 신호로 동작).
 */
export function useAdminSystemCron() {
  return useQuery<CronJobsResponse>({
    queryKey: qk.adminSystemCron(),
    queryFn: () => api.adminGetCronStatus(),
    retry: false,
    staleTime: 30_000,
  })
}
