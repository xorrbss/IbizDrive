'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

/**
 * 휴지통 이동 (soft delete) bulk mutation. M9.1에서 Mock `api.deleteBulk` 제거 후
 * backend per-item endpoint(`DELETE /api/files/:id` / `DELETE /api/folders/:id`)를
 * 병렬 호출. 일부만 실패하면 첫 실패가 전체 throw — onError가 selection 복원.
 *
 * 시그니처가 `ids: string[]` → `items: { id, type }[]`로 바뀐 이유는 backend 분기를
 * 호출 시점에서 결정해야 하기 때문 (선택 단위에 file/folder 혼재 가능). 호출자는
 * FileItem 캐시에서 type을 함께 묶어 전달한다.
 */
type DeleteItem = { id: string; type: 'file' | 'folder' }
type Vars = { items: DeleteItem[]; folderIdAtStart: string }

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
    mutationFn: async ({ items }: Vars) => {
      // Promise.all → 첫 rejection이 전체 실패를 결정. backend는 idempotent (이미 삭제된
      // row는 404) — 부분 성공 정밀 처리는 v1.x. 본 마일스톤에서는 onError 폴백으로 충분.
      await Promise.all(
        items.map((it) =>
          it.type === 'folder' ? api.softDeleteFolder(it.id) : api.softDeleteFile(it.id),
        ),
      )
    },

    onMutate: ({ items }: Vars) => {
      markPending(items.map((i) => i.id))
    },

    onSuccess: async (_data, vars: Vars) => {
      await invalidations.afterDelete(qc, { folderId: vars.folderIdAtStart })
      unmarkPending(vars.items.map((i) => i.id))
      clear()
      options.onSuccess?.(vars)
    },

    onError: (err, vars: Vars) => {
      const ids = vars.items.map((i) => i.id)
      unmarkPending(ids)
      if (vars.folderIdAtStart === currentFolderId) {
        selectAll(ids)
      }
      options.onError?.(err, vars)
    },
  })
}
