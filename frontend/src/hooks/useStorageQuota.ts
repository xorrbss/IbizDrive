'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * 저장 용량 조회 (M15 docs/01 §18 row 15).
 * staleTime 5분 — quota는 자주 변하지 않음. 업로드/삭제 시점 invalidate는 v1.x.
 */
export function useStorageQuota() {
  return useQuery({
    queryKey: qk.storageQuota(),
    queryFn: () => api.getStorageQuota(),
    staleTime: 5 * 60_000,
  })
}
