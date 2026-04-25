'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * 휴지통 영구 삭제.
 *
 * docs/01 §14.3 — 파괴적 + 위험. UI는 admin 권한일 때만 노출.
 */
export function usePurgeFiles() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (ids: string[]) => api.purgeFiles(ids),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.trashList() })
    },
  })
}
