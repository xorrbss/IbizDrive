export type MoveDragData = {
  type: 'move-files'
  ids: string[]
  sourceFolderId: string
  /** ids 중 폴더인 것만. self/descendant 판정에 사용. */
  containsFolderIds: string[]
  /** 드래그 출발 workspace 정보. droppable이 cross-workspace 여부 판정에 사용. */
  sourceWorkspace: { kind: 'department' | 'team' | 'shared'; id: string | null }
}

export const DROPPABLE_FOLDER_PREFIX = 'folder-'
export const DRAGGABLE_ROW_PREFIX = 'row-'

export function parseFolderDroppableId(id: string | number): string | null {
  if (typeof id !== 'string' || !id.startsWith(DROPPABLE_FOLDER_PREFIX)) return null
  return id.slice(DROPPABLE_FOLDER_PREFIX.length)
}
