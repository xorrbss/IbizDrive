'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

type Vars = { ids: string[]; folderIdAtStart: string }

type Options = {
  onSuccess?: (vars: Vars) => void
  onError?: (err: unknown, vars: Vars) => void
}

export function useDeleteBulk(options: Options = {}) {
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

    onSuccess: async (_data, vars: Vars) => {
      await invalidations.afterDelete(qc, { folderId: vars.folderIdAtStart })
      unmarkPending(vars.ids)
      clear()
      options.onSuccess?.(vars)
    },

    onError: (err, vars: Vars) => {
      unmarkPending(vars.ids)
      if (vars.folderIdAtStart === currentFolderId) {
        selectAll(vars.ids)
      }
      options.onError?.(err, vars)
    },
  })
}
