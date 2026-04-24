'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * 단일 파일 상세 (RightPanel 용).
 * 캐시 키: qk.fileDetail(id) — docs/01 §6.1
 */
export function useFileDetail(id: string | null) {
  return useQuery({
    queryKey: id ? qk.fileDetail(id) : ['fileDetail', 'null'],
    queryFn: () => {
      if (!id) throw new Error('useFileDetail called without id')
      return api.getFileDetail(id)
    },
    enabled: Boolean(id),
    staleTime: 30_000,
  })
}
