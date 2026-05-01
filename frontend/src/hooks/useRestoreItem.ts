'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { TrashItemType } from '@/types/trash'

type Vars = {
  type: TrashItemType
  id: string
  /**
   * 원위치 부모 폴더 id. 알려져 있으면 `invalidations.afterRestore({folderIds:[id]})`로 정밀 무효화.
   * 미지정 시 `qk.files()` 보수 무효화 (전체 파일 목록 prefix).
   */
  sourceFolderId?: string
}

/**
 * 휴지통 항목 복원 mutation (M9.2). 409 RESTORE_CONFLICT 시 onError로 surface
 * (api.restore* 에서 envelope 파싱하여 err.code='RESTORE_CONFLICT'). UX layer가 분기.
 *
 * 무효화 매트릭스 (docs/01 §6.2):
 * - trash + search + folderTree (afterRestore 공통)
 * - sourceFolderId 있으면 filesListPrefix(id) / 없으면 files() prefix
 */
export function useRestoreItem() {
  const qc = useQueryClient()

  return useMutation<void, Error, Vars>({
    mutationFn: ({ type, id }) =>
      type === 'folder' ? api.restoreFolder(id) : api.restoreFile(id),

    onSuccess: async (_data, vars) => {
      await invalidations.afterRestore(qc, {
        folderIds: vars.sourceFolderId ? [vars.sourceFolderId] : undefined,
      })
    },
  })
}
