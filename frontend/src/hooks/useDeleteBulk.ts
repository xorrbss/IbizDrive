'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

type Vars = { ids: string[]; folderIdAtStart: string }

/**
 * 휴지통으로 이동 + 5초 Undo 토스트 (M9, docs/01 §13.2).
 *
 * - 성공 시 sonner toast 표시: "{N}개 항목 휴지통으로 이동" + [되돌리기]
 * - [되돌리기] 클릭 → api.restoreFiles. 5초 후 자동 dismiss (mock 단계 — 실제 백엔드는 30일 후 purge)
 * - 실패 시 selection 복구 (현재 폴더에 머물러 있을 때만)
 */
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
      await qc.invalidateQueries({ queryKey: qk.trashList() })
      unmarkPending(ids)
      clear()

      toast(`${ids.length}개 항목을 휴지통으로 이동했습니다`, {
        action: {
          label: '되돌리기',
          onClick: async () => {
            try {
              await api.restoreFiles(ids)
              await qc.invalidateQueries({
                queryKey: [...qk.files(), 'list', folderIdAtStart],
              })
              await qc.invalidateQueries({ queryKey: qk.trashList() })
              toast.success('복원되었습니다')
            } catch (e) {
              const code = (e as { code?: string })?.code
              toast.error(
                code === 'RESTORE_CONFLICT'
                  ? '같은 이름의 파일/폴더가 이미 있어 복원할 수 없습니다'
                  : '복원 실패',
              )
            }
          },
        },
        duration: 5000,
      })
    },

    onError: (_err, { ids, folderIdAtStart }: Vars) => {
      unmarkPending(ids)
      if (folderIdAtStart === currentFolderId) {
        selectAll(ids)
      }
      toast.error('휴지통으로 이동 실패')
    },
  })
}
