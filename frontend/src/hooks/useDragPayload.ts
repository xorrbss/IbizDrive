'use client'
import { useQueryClient } from '@tanstack/react-query'
import { useSelectionStore } from '@/stores/selection'
import { qk } from '@/lib/queryKeys'
import type { FileItem } from '@/types/file'
import type { MoveDragData } from '@/components/dnd/types'

/**
 * 드래그 시작 시점의 ids/sourceFolderId/containsFolderIds 산출.
 * - rowId가 selection에 있으면 selection 전체, 아니면 [rowId]
 * - containsFolderIds: ids 중 type='folder'만 (filesInFolder 캐시에서 매칭)
 */
export function useDragPayload(rowId: string, rowParentId: string): MoveDragData {
  const selectedIds = useSelectionStore((s) => s.ids)
  const qc = useQueryClient()

  const ids = selectedIds.has(rowId)
    ? Array.from(selectedIds)
    : [rowId]

  // sort/dir 변종 중 첫 매치를 사용 (type 정보는 정렬과 무관하게 일관)
  const matches = qc.getQueriesData<FileItem[]>({
    queryKey: [...qk.files(), 'list', rowParentId],
  })
  const items = matches[0]?.[1] ?? []

  const containsFolderIds = items
    .filter((f) => ids.includes(f.id) && f.type === 'folder')
    .map((f) => f.id)

  return {
    type: 'move-files',
    ids,
    sourceFolderId: rowParentId,
    containsFolderIds,
  }
}
