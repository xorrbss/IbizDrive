'use client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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

/**
 * admin-cron-policy-toggle — `PUT /api/admin/system/cron/{key}` mutation hook.
 *
 * <p>onSuccess 시 `qk.adminSystemCron()` 무효화 → viewer가 staleTime(30s) 무시하고
 * 즉시 refetch하여 토글 결과 반영. 본 hook은 ADMIN-only 페이지에서만 호출되지만,
 * 실제 권한 보장은 backend `@PreAuthorize("hasRole('ADMIN')")` (진실 출처).
 *
 * <p>retry는 react-query 기본값 사용 안 함 — mutation은 사용자 명시 액션이므로
 * 실패 즉시 표면화 (UI에서 Confirm dialog 닫지 않고 에러 노출).
 */
export function useAdminToggleCron() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      api.adminToggleCron(key, enabled),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.adminSystemCron() })
    },
  })
}
