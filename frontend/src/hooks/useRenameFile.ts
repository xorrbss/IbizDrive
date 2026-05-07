'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'
import { useRenameUiStore } from '@/stores/renameUi'

type Vars = {
  id: string
  newName: string
  parentId: string
  isFolder: boolean
}

type Options = {
  onSuccess?: (vars: Vars) => void
  onError?: (err: unknown, vars: Vars) => void
}

function errorMessage(err: unknown): string {
  const e = err as { code?: string }
  if (e?.code === 'RENAME_CONFLICT') return '같은 이름의 파일/폴더가 있습니다'
  if (e?.code === 'VALIDATION_ERROR') return '이름은 비어있을 수 없습니다'
  return '이름 변경에 실패했습니다'
}

/**
 * 파일/폴더 이름 변경 mutation.
 * 원칙 #3 — 낙관적 업데이트 없음. pending 마킹만, 서버 응답 후 invalidate.
 * 원칙 #6 — 서버가 진실. client-side validation은 빈 입력만(UX), 충돌은 서버 응답으로.
 *
 * onSuccess는 closeDialog 직전에 호출되므로 unmount 영향 없음.
 * onError는 setError(다이얼로그 inline) 후 호출 — 토스트가 필요한 경우 호출부에서 결정.
 */
export function useRenameFile(options: Options = {}) {
  const qc = useQueryClient()
  const markPending = useSelectionStore((s) => s.markPending)
  const unmarkPending = useSelectionStore((s) => s.unmarkPending)
  const closeDialog = useRenameUiStore((s) => s.close)
  const setError = useRenameUiStore((s) => s.setError)

  return useMutation({
    mutationFn: ({ id, newName, isFolder }: Vars) => api.renameFile(id, newName, isFolder),

    onMutate: ({ id }: Vars) => {
      markPending([id])
      setError(null)
    },

    onSuccess: async (_data, vars: Vars) => {
      await invalidations.afterRename(qc, {
        id: vars.id,
        parentId: vars.parentId,
        isFolder: vars.isFolder,
      })
      unmarkPending([vars.id])
      options.onSuccess?.(vars)
      closeDialog()
    },

    onError: (err, vars: Vars) => {
      unmarkPending([vars.id])
      // 다이얼로그 inline 에러는 setError로 유지 (사용자가 입력값 수정해 재시도)
      setError(errorMessage(err))
      options.onError?.(err, vars)
    },
  })
}
