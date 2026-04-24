'use client'
import { useDndContext, useDroppable } from '@dnd-kit/core'
import { useFolderTree } from '@/hooks/useFolderTree'
import { isSelfOrDescendantOfAny } from '@/lib/folderTreeUtils'
import { DROPPABLE_FOLDER_PREFIX, type MoveDragData } from './types'

/**
 * 폴더를 드롭 타겟으로 등록.
 * - dragData가 자기/후손/같은-폴더이면 disabled (드롭 차단)
 * - 시각화는 호출 측이 isOver/isInvalid/isSameFolder/isDragging 플래그로 결정
 */
export function useFolderDroppable(folderId: string) {
  const { active } = useDndContext()
  const { data: tree } = useFolderTree()
  const dragData = active?.data.current as MoveDragData | undefined

  const isInvalid =
    !!dragData &&
    isSelfOrDescendantOfAny(tree, dragData.containsFolderIds, folderId)

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
