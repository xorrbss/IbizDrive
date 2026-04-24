'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

type Vars = { ids: string[]; folderIdAtStart: string }

export function useDeleteBulk() {
  const qc = useQueryClient()
  const markPending = useSelectionStore((s) => s.markPending)
  const unmarkPending = useSelectionStore((s) => s.unmarkPending)
  const clear = useSelectionStore((s) => s.clear)
  const selectAll = useSelectionStore((s) => s.selectAll)
  const { folderId: currentFolderId } = useCurrentFolder()

  return useMutation({
    mutationFn: ({ ids }: Vars) => api.deleteBulk(ids),

    onMutate: ({ ids }: Vars) => {
      markPending(ids)
    },

    onSuccess: async (_data, { ids, folderIdAtStart }: Vars) => {
      await qc.invalidateQueries({
        queryKey: [...qk.files(), 'list', folderIdAtStart],
      })
      unmarkPending(ids)
      clear()
    },

    onError: (_err, { ids, folderIdAtStart }: Vars) => {
      unmarkPending(ids)
      if (folderIdAtStart === currentFolderId) {
        selectAll(ids)
      }
      // TODO(M5): 토스트 에러 알림
      console.warn('deleteBulk 실패', { ids, folderIdAtStart })
    },
  })
}
