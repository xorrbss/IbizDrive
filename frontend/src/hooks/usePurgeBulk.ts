'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

type Vars = { ids: string[] }
type Options = {
  onSuccess?: (vars: Vars) => void
  onError?: (err: unknown, vars: Vars) => void
}

/**
 * 휴지통 영구 삭제 (M9). 다른 keyspace는 이미 deletedAt!=null 필터로 제외 중이므로
 * trash list만 무효화.
 */
export function usePurgeBulk(options: Options = {}) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ ids }: Vars) => api.purgeBulk(ids),
    onSuccess: async (_data, vars: Vars) => {
      await invalidations.afterPurge(qc)
      options.onSuccess?.(vars)
    },
    onError: (err, vars: Vars) => {
      options.onError?.(err, vars)
    },
  })
}
