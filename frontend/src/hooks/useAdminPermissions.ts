'use client'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AdminPermissionFilters, AdminPermissionPage } from '@/types/permission'

/**
 * Admin 권한 매트릭스 hook (admin-permission-matrix, Wave 2 T5).
 *
 * <p>read-only — invalidations entry 없음. q는 호출 측이 trim+lowercase 후 전달
 * (T4 useAdminDepartments 동일 정책). 캐시 키 안정성을 위해 normalizedQ 적용.
 *
 * <p>page 전환 깜빡임 완화: keepPreviousData (TanStack Query v5 — placeholderData에 keepPreviousData 함수).
 */
export function useAdminPermissions(filters: AdminPermissionFilters) {
  const normalized: AdminPermissionFilters = {
    subjectType: filters.subjectType,
    subjectId: filters.subjectId?.trim() || undefined,
    resourceType: filters.resourceType,
    preset: filters.preset,
    q: filters.q?.trim().toLowerCase() || undefined,
    page: filters.page ?? 0,
    size: filters.size ?? 20,
  }
  return useQuery<AdminPermissionPage>({
    queryKey: qk.adminPermissionsList(normalized),
    queryFn: () => api.adminListPermissions(normalized),
    retry: false,
    staleTime: 0,
    placeholderData: keepPreviousData,
  })
}
