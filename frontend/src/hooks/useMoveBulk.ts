'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'

type Vars = {
  ids: string[]
  sourceFolderId: string
  targetFolderId: string
}

type Options = {
  /** 성공 콜백. 다이얼로그 즉시 close 패턴에서도 unmount 후 안전하게 호출됨 (hook-level 등록). */
  onSuccess?: (vars: Vars) => void
  onError?: (err: unknown, vars: Vars) => void
}

/**
 * 파일/폴더 이동 mutation. DnD와 다이얼로그 두 진입점이 공통으로 사용.
 * 원칙 #3 — 낙관적 업데이트 없음. pending 마킹만, 서버 응답 후 invalidate.
 *
 * onSuccess/onError를 mutate(_, options)에 넘기는 대신 useMoveBulk(options)에 넘기는 이유:
 * TanStack Query v5에서 mutate 호출별 callback은 component unmount 시 호출되지 않음.
 * MoveFolderDialog는 mutate 직후 close() → unmount하므로 hook-level callback이 필요.
 */
export function useMoveBulk(options: Options = {}) {
  const qc = useQueryClient()
  const markPending = useSelectionStore((s) => s.markPending)
  const unmarkPending = useSelectionStore((s) => s.unmarkPending)
  const clear = useSelectionStore((s) => s.clear)

  return useMutation({
    mutationFn: ({ ids, targetFolderId }: Vars) =>
      api.moveFiles(ids, targetFolderId),

    onMutate: ({ ids }: Vars) => {
      markPending(ids)
    },

    onSuccess: async (_data, vars: Vars) => {
      // §6.2 무효화 매트릭스 — invalidations.afterFilesMoved (lib/queryKeys.ts)
      await invalidations.afterFilesMoved(qc, {
        sourceFolderId: vars.sourceFolderId,
        targetFolderId: vars.targetFolderId,
        ids: vars.ids,
      })
      unmarkPending(vars.ids)
      clear()
      options.onSuccess?.(vars)
    },

    onError: (err, vars: Vars) => {
      unmarkPending(vars.ids)
      options.onError?.(err, vars)
    },
  })
}
