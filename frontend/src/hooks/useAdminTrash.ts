'use client'
import {
  useMutation,
  useQuery,
  useQueryClient,
  keepPreviousData,
} from '@tanstack/react-query'
import { adminListTrash, api } from '@/lib/api'
import { invalidations, qk } from '@/lib/queryKeys'
import type {
  AdminTrashFilters,
  AdminTrashPage,
  TrashItemType,
} from '@/types/trash'

/**
 * Wave 2 T9 — admin global trash 목록 쿼리. docs/01 §6.1, ADR #32 정합.
 *
 * - queryKey: `qk.adminTrashList(filters, cursor)` (prefix `qk.adminTrash()`로 일괄 무효화).
 * - placeholderData: keepPreviousData — 필터/cursor 전환 시 직전 페이지 유지.
 * - cursor=null이면 첫 페이지.
 */
export function useAdminTrashList(filters: AdminTrashFilters, cursor: string | null) {
  return useQuery<AdminTrashPage>({
    queryKey: qk.adminTrashList(filters, cursor),
    queryFn: () => adminListTrash(filters, cursor),
    placeholderData: keepPreviousData,
  })
}

export interface AdminTrashTarget {
  id: string
  type: TrashItemType
}

/**
 * Wave 2 T9 — admin restore mutation. ADMIN role이 backend SpEL guard를 통과하므로
 * 일반 user endpoint(`api.restoreFile` / `api.restoreFolder`)를 그대로 재사용.
 *
 * 성공 시 `qk.adminTrash()` prefix 단독 무효화. 일반 trash/folder/search 등 다른 keyspace는
 * admin trash 페이지 컨텍스트에서는 무관하므로 추가 무효화 불필요.
 */
export function useAdminRestoreTrashItem() {
  const qc = useQueryClient()

  return useMutation<void, Error, AdminTrashTarget>({
    mutationFn: ({ id, type }) =>
      type === 'file' ? api.restoreFile(id) : api.restoreFolder(id),
    onSuccess: () => invalidations.afterAdminTrashChanged(qc),
  })
}

/**
 * Wave 2 T9 — admin purge mutation. ADMIN role이 backend SpEL guard를 통과하므로
 * 일반 user endpoint(`api.purgeTrashItem`)를 그대로 재사용.
 *
 * 성공 시 `qk.adminTrash()` prefix 단독 무효화.
 */
export function useAdminPurgeTrashItem() {
  const qc = useQueryClient()

  return useMutation<void, Error, AdminTrashTarget>({
    mutationFn: ({ id, type }) => api.purgeTrashItem(type, id),
    onSuccess: () => invalidations.afterAdminTrashChanged(qc),
  })
}
