'use client'
import { useQuery } from '@tanstack/react-query'
import { api, type AdminUserPage } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * Admin 사용자 목록 (admin-user-mgmt + admin-user-search-update, docs/02 §7.4).
 *
 * <p>page/size/q를 키에 포함 — 변경 시 자동 재요청. PATCH 후 prefix 매칭으로 모든
 * 변종이 한 번에 무효화된다 ({@link invalidations.afterAdminUserChanged}).
 *
 * <p>{@code q}는 옵션 — 호출자가 debounce(300ms 권장) 후 전달. trim된 빈 문자열과 undefined는
 * 동일 취급(전체 목록). qk가 normalize 처리하므로 호출자는 raw 입력을 그대로 넘겨도 안전.
 *
 * <p>{@code staleTime}은 0 (즉시 stale) — admin 화면은 리얼타임 정확성이 우선이고 트래픽
 * 부담은 작은 도메인이므로 보수적 갱신 정책. 401/403은 react-query의 retry false로 즉시 노출.
 */
export function useAdminUsers(page = 0, size = 50, q = '') {
  return useQuery<AdminUserPage>({
    queryKey: qk.adminUsersList(page, size, q),
    queryFn: () => api.adminListUsers(page, size, q),
    retry: false,
    staleTime: 0,
  })
}
