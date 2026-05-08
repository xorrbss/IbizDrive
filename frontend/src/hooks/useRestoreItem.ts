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
  /**
   * 새 이름으로 복원 (v1.x RestoreConflictDialog). 미지정 시 원본 이름 그대로.
   * 지정 시 backend 가 NFC 정규화 + UNIQUE 재검사, 충돌 시 'RENAME_CONFLICT' envelope.
   */
  newName?: string
}

/**
 * 휴지통 항목 복원 mutation (M9.2 + v1.x newName 지원). onError 분기 코드:
 * - 'RESTORE_CONFLICT' — 원본 이름 충돌 (newName 미지정), UX 가 RestoreConflictDialog 띄움.
 * - 'RENAME_CONFLICT'  — 새 이름 충돌 (newName 지정), 다이얼로그 inline alert.
 *
 * 무효화 매트릭스 (docs/01 §6.2):
 * - trash + search + folderTree (afterRestore 공통)
 * - sourceFolderId 있으면 filesListPrefix(id) / 없으면 files() prefix
 */
export function useRestoreItem() {
  const qc = useQueryClient()

  return useMutation<void, Error, Vars>({
    mutationFn: ({ type, id, newName }) => {
      if (type === 'folder') {
        return newName !== undefined
          ? api.restoreFolder(id, { newName })
          : api.restoreFolder(id)
      }
      return newName !== undefined
        ? api.restoreFile(id, { newName })
        : api.restoreFile(id)
    },

    onSuccess: async (_data, vars) => {
      await invalidations.afterRestore(qc, {
        folderIds: vars.sourceFolderId ? [vars.sourceFolderId] : undefined,
      })
    },
  })
}
