'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'

type Vars = {
  ids: string[]
  sourceFolderId: string
  targetFolderId: string
}

/**
 * 파일/폴더 이동 mutation. DnD와 다이얼로그 두 진입점이 공통으로 사용.
 * 원칙 #3 — 낙관적 업데이트 없음. pending 마킹만, 서버 응답 후 invalidate.
 */
export function useMoveBulk() {
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

    onSuccess: async (_data, { ids, sourceFolderId, targetFolderId }: Vars) => {
      // §6.2 무효화 매트릭스
      await Promise.all([
        qc.invalidateQueries({ queryKey: [...qk.files(), 'list', sourceFolderId] }),
        qc.invalidateQueries({ queryKey: [...qk.files(), 'list', targetFolderId] }),
        qc.invalidateQueries({ queryKey: qk.folderTree() }),
        ...ids.map((id) => qc.invalidateQueries({ queryKey: qk.fileDetail(id) })),
      ])
      unmarkPending(ids)
      clear()
    },

    onError: (_err, { ids }: Vars) => {
      unmarkPending(ids)
      // 토스트는 M_toast에서 추가
      console.warn('moveBulk 실패', { ids })
    },
  })
}
