'use client'
import { useQuery } from '@tanstack/react-query'
import { getAdminTrashPolicy } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AdminTrashPolicy } from '@/types/admin-trash-policy'

/**
 * Admin 휴지통 보존 정책 read-only viewer (wave2-trash-policy-viewer).
 *
 * <p>read-only 단일 페이지 — page/size/filter 인자 없음. mutation 없음 → invalidation 짝 없음.
 * `staleTime` 60초 — 보존 정책은 자주 변경되지 않음(yml + 재기동). 401/403은 `retry: false`로
 * 즉시 노출.
 */
export function useAdminTrashPolicy() {
  return useQuery<AdminTrashPolicy>({
    queryKey: qk.adminTrashPolicy(),
    queryFn: getAdminTrashPolicy,
    retry: false,
    staleTime: 60_000,
  })
}
