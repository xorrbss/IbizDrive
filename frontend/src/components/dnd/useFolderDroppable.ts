'use client'
import { useDndContext, useDroppable } from '@dnd-kit/core'
import { DROPPABLE_FOLDER_PREFIX, type MoveDragData } from './types'
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'

/**
 * 폴더를 드롭 타겟으로 등록.
 *
 * drop target이 cross-workspace이면 disabled + isCrossWorkspace flag 노출.
 * "공유받음" 섹션은 항상 drop 차단 (re-share 금지, spec §4.5 §6, §4.3).
 *
 * Plan B lazy-tree 모델에서 self/descendant 판정은 보수적으로 수행:
 *   containsFolderIds.includes(folderId) || sourceFolderId === folderId
 * 정확한 subtree 검사는 backend가 담당 (부분 로드 트리에서 frontend 전체 판정 불가).
 */
export function useFolderDroppable(
  folderId: string,
  /** drop target의 workspace context — sidebar tree에서 호출 시 명시 전달. 미지정이면 useCurrentWorkspace fallback. */
  targetWorkspace?: { kind: 'department' | 'team' | 'shared'; id: string | null },
) {
  const { active } = useDndContext()
  const dragData = active?.data.current as MoveDragData | undefined
  const wsCurrent = useCurrentWorkspace()
  const target =
    targetWorkspace ??
    (wsCurrent
      ? { kind: wsCurrent.section, id: wsCurrent.workspaceId }
      : { kind: 'shared' as const, id: null })

  const isCrossWorkspace =
    !!dragData &&
    (dragData.sourceWorkspace.kind !== target.kind ||
      dragData.sourceWorkspace.id !== target.id)

  // 보수적 self/descendant 방어: containsFolderIds + sourceFolderId 비교.
  // 정확한 subtree 판정은 backend 거부로 보완.
  const isInvalid =
    !!dragData &&
    (dragData.containsFolderIds.includes(folderId) ||
      dragData.sourceFolderId === folderId)

  const isSameFolder = !!dragData && dragData.sourceFolderId === folderId

  // "공유받음" 섹션은 항상 drop 차단
  const isSharedTarget = target.kind === 'shared'

  const { isOver, setNodeRef } = useDroppable({
    id: `${DROPPABLE_FOLDER_PREFIX}${folderId}`,
    disabled: isInvalid || isSameFolder || isCrossWorkspace || isSharedTarget,
  })

  return {
    isOver,
    setNodeRef,
    isInvalid,
    isSameFolder,
    isCrossWorkspace,
    isSharedTarget,
    isDragging: !!dragData,
  }
}
