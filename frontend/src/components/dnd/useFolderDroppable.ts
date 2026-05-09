'use client'
import { useDndContext, useDroppable } from '@dnd-kit/core'
import { isSelfOrDescendantOfAny } from '@/lib/folderTreeUtils'
import { DROPPABLE_FOLDER_PREFIX, type MoveDragData } from './types'

/**
 * 폴더를 드롭 타겟으로 등록.
 * - dragData가 자기/후손/같은-폴더이면 disabled (드롭 차단)
 * - 시각화는 호출 측이 isOver/isInvalid/isSameFolder/isDragging 플래그로 결정
 *
 * TODO: [BLOCKED]
 *   violated: YAGNI / 기존 구조 우선
 *   reason: useFolderTree (flat tree) 제거됨. Plan B lazy per-workspace tree (Tasks 17+) 미구현.
 *   required_change: Tasks 17+ 구현 후 useFolderChildren 기반 tree로 descendant 검사 복원.
 *   현재: tree=undefined → isSelfOrDescendantOfAny가 false 반환 → 드롭 차단 미동작 (안전 degradation).
 */
export function useFolderDroppable(folderId: string) {
  const { active } = useDndContext()
  const dragData = active?.data.current as MoveDragData | undefined

  const isInvalid =
    !!dragData &&
    isSelfOrDescendantOfAny(undefined, dragData.containsFolderIds, folderId)

  const isSameFolder = !!dragData && dragData.sourceFolderId === folderId

  const { isOver, setNodeRef } = useDroppable({
    id: `${DROPPABLE_FOLDER_PREFIX}${folderId}`,
    disabled: isInvalid || isSameFolder,
  })

  return {
    isOver,
    setNodeRef,
    isInvalid,
    isSameFolder,
    isDragging: !!dragData,
  }
}
