'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

type Vars = {
  ids: string[]
  /**
   * 복원 대상 항목들의 originalParentId 모음. UI(휴지통/Undo 토스트)는 이 정보를
   * 캐시에서 미리 알 수 있으므로 hook 진입 시 함께 전달.
   * 모르면 생략 → invalidations.afterRestore가 files() prefix 보수 무효화.
   */
  originalParentIds?: string[]
}

type Options = {
  onSuccess?: (vars: Vars) => void
  onError?: (err: unknown, vars: Vars) => void
}

/**
 * 휴지통 복원 (M9). 다중 id 배치 처리 + 캐시 무효화.
 */
export function useRestoreBulk(options: Options = {}) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ ids }: Vars) => api.restoreBulk(ids),
    onSuccess: async (_data, vars: Vars) => {
      await invalidations.afterRestore(qc, { folderIds: vars.originalParentIds })
      options.onSuccess?.(vars)
    },
    onError: (err, vars: Vars) => {
      options.onError?.(err, vars)
    },
  })
}
