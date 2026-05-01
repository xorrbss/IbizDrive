'use client'
import { useInfiniteQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { SharePage } from '@/types/share'

/**
 * 내가 공유한 목록 무한 페이지 쿼리 (F4, docs/02 §7.9).
 *
 * - queryKey: `qk.sharesByMe()` — `qk.shares()` prefix로 invalidate 매칭.
 * - getNextPageParam: backend가 nextCursor 누락 → null → undefined 정규화 (hasNextPage=false).
 */
export function useSharesByMe() {
  return useInfiniteQuery<SharePage, Error>({
    queryKey: qk.sharesByMe(),
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.listSharesByMe({ cursor: pageParam as string | undefined }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
