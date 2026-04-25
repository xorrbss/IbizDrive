'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'
import { useRenameUiStore } from '@/stores/renameUi'

type Vars = {
  id: string
  newName: string
  parentId: string
  isFolder: boolean
}

function errorMessage(err: unknown): string {
  const e = err as { code?: string }
  if (e?.code === 'NAME_CONFLICT') return '같은 이름의 파일/폴더가 있습니다'
  if (e?.code === 'INVALID_NAME') return '이름은 비어있을 수 없습니다'
  return '이름 변경에 실패했습니다'
}

/**
 * 파일/폴더 이름 변경 mutation.
 * 원칙 #3 — 낙관적 업데이트 없음. pending 마킹만, 서버 응답 후 invalidate.
 * 원칙 #6 — 서버가 진실. client-side validation은 빈 입력만(UX), 충돌은 서버 응답으로.
 */
export function useRenameFile() {
  const qc = useQueryClient()
  const markPending = useSelectionStore((s) => s.markPending)
  const unmarkPending = useSelectionStore((s) => s.unmarkPending)
  const closeDialog = useRenameUiStore((s) => s.close)
  const setError = useRenameUiStore((s) => s.setError)

  return useMutation({
    mutationFn: ({ id, newName }: Vars) => api.renameFile(id, newName),

    onMutate: ({ id }: Vars) => {
      markPending([id])
      setError(null)
    },

    onSuccess: async (_data, { id, parentId, isFolder }: Vars) => {
      const invalidations = [
        qc.invalidateQueries({ queryKey: [...qk.files(), 'list', parentId] }),
        qc.invalidateQueries({ queryKey: qk.fileDetail(id) }),
      ]
      if (isFolder) {
        invalidations.push(qc.invalidateQueries({ queryKey: qk.folderTree() }))
        invalidations.push(qc.invalidateQueries({ queryKey: qk.folder(id) }))
      }
      await Promise.all(invalidations)
      unmarkPending([id])
      closeDialog()
    },

    onError: (err, { id }: Vars) => {
      unmarkPending([id])
      setError(errorMessage(err))
      console.warn('renameFile 실패', err)
    },
  })
}
