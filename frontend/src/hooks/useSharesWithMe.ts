'use client'
import { useInfiniteQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { SharePage } from '@/types/share'

/**
 * 받은 공유 목록 무한 페이지 쿼리 (F4, MVP scope).
 *
 * MVP: backend는 subject_type='user' 매칭만 — department/role/everyone 으로 받은 share 미포함
 * (별도 트랙, ADR #34 backlog). UI는 결과를 그대로 렌더.
 */
export function useSharesWithMe() {
  return useInfiniteQuery<SharePage, Error>({
    queryKey: qk.sharesWithMe(),
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.listSharesWithMe({ cursor: pageParam as string | undefined }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
