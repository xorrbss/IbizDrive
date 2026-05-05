'use client'
import { useQuery } from '@tanstack/react-query'
import { api, type AdminUserPage } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * Admin 사용자 목록 (admin-user-mgmt, docs/02 §7.4).
 *
 * <p>page/size를 키에 포함 — 페이지 변경 시 자동 재요청. PATCH 후 prefix 매칭으로 모든
 * 페이지 변종이 한 번에 무효화된다 ({@link invalidations.afterAdminUserChanged}).
 *
 * <p>{@code staleTime}은 0 (즉시 stale) — admin 화면은 리얼타임 정확성이 우선이고 트래픽
 * 부담은 작은 도메인이므로 보수적 갱신 정책. 401/403은 react-query의 retry false로 즉시 노출.
 */
export function useAdminUsers(page = 0, size = 50) {
  return useQuery<AdminUserPage>({
    queryKey: qk.adminUsersList(page, size),
    queryFn: () => api.adminListUsers(page, size),
    retry: false,
    staleTime: 0,
  })
}
