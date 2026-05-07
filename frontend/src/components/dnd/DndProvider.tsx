'use client'
import { useState } from 'react'
import { toast } from 'sonner'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { useMoveBulk } from '@/hooks/useMoveBulk'
import { MoveDragOverlay } from './MoveDragOverlay'
import { parseFolderDroppableId, type MoveDragData } from './types'

/**
 * 이동 전용 DndContext. 업로드용 window 네이티브 DnD와 분리 (원칙 #7).
 * - PointerSensor + activationConstraint(distance:5px) — 클릭과 드래그 구분
 * - DragOverlay = 카운트 배지 (행 복제 X)
 */
export function DndProvider({ children }: { children: React.ReactNode }) {
  const [activeData, setActiveData] = useState<MoveDragData | null>(null)
  const moveBulk = useMoveBulk({
    onSuccess: (vars) => toast.success(`${vars.items.length}개 항목을 이동했습니다`),
    onError: () => toast.error('이동에 실패했습니다. 다시 시도해 주세요.'),
  })

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 5 },
    }),
  )

  const handleDragStart = (e: DragStartEvent) => {
    const data = e.active.data.current as MoveDragData | undefined
    if (data?.type === 'move-files') {
      setActiveData(data)
    }
  }

  const handleDragEnd = (e: DragEndEvent) => {
    const data = activeData
    setActiveData(null)
    if (!data || !e.over) return

    const targetFolderId = parseFolderDroppableId(e.over.id)
    if (!targetFolderId) return

    // 같은 폴더 = no-op
    if (targetFolderId === data.sourceFolderId) return
    // 자기/후손 — useFolderDroppable의 disabled가 1차로 막음. 방어적 재검증.
    if (data.containsFolderIds.includes(targetFolderId)) return

    // backend는 file/folder 분기 endpoint이므로 useMoveBulk에 type 정보를 함께 전달.
    // MoveDragData.containsFolderIds는 ids 중 폴더인 것의 부분집합 — 나머지는 file로 결정.
    const folderSet = new Set(data.containsFolderIds)
    const items = data.ids.map((id) => ({
      id,
      type: (folderSet.has(id) ? 'folder' : 'file') as 'file' | 'folder',
    }))
    moveBulk.mutate({
      items,
      sourceFolderId: data.sourceFolderId,
      targetFolderId,
    })
  }

  const handleDragCancel = () => setActiveData(null)

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      {children}
      <DragOverlay dropAnimation={null}>
        {activeData && <MoveDragOverlay count={activeData.ids.length} />}
      </DragOverlay>
    </DndContext>
  )
}
