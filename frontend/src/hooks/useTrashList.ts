'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * 휴지통 목록 조회 (M9 docs/01 §13).
 *
 * - staleTime 0: 휴지통 진입 시마다 신선한 상태 확인 (delete/restore 직후 reflect 보장).
 * - 다른 탭 변경은 SSE(M10) 도입 후 push 무효화 예정.
 */
export function useTrashList() {
  return useQuery({
    queryKey: qk.trashList(),
    queryFn: () => api.listTrash(),
    staleTime: 0,
  })
}
