'use client'
import { useInfiniteQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { TrashItemType, TrashPage } from '@/types/trash'

/**
 * 휴지통 무한 페이지 쿼리 (M9.2). docs/01 §6.1·§13, ADR #32 정합.
 *
 * - queryKey: `qk.trashList()` (전체) 또는 `[...qk.trashList(), type]` (필터)
 *   → invalidateQueries({ queryKey: qk.trash() })가 prefix로 모두 매칭.
 * - getNextPageParam: backend가 마지막 페이지에서 nextCursor 키를 생략(NON_NULL) 또는 null
 *   → undefined로 정규화하여 hasNextPage=false.
 * - initialPageParam: undefined — 첫 페이지는 cursor 없이 요청.
 */
export function useTrashList(opts: { type?: TrashItemType } = {}) {
  const { type } = opts
  const queryKey = type
    ? ([...qk.trashList(), type] as const)
    : qk.trashList()

  return useInfiniteQuery<TrashPage, Error>({
    queryKey,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.getTrash({ cursor: pageParam as string | undefined, type }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
