// frontend/src/components/files/FileRow.tsx
'use client'
import { useDraggable } from '@dnd-kit/core'
import { useDragPayload } from '@/hooks/useDragPayload'
import { useFolderDroppable } from '@/components/dnd/useFolderDroppable'
import { DRAGGABLE_ROW_PREFIX } from '@/components/dnd/types'
import { getFileIcon, getFileIconColor } from '@/lib/fileIcons'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  rowIndex: number
  isFocused: boolean
  isSelected: boolean
  isPending: boolean
  onClick?: (item: FileItem, e: React.MouseEvent) => void
  onDoubleClick?: (item: FileItem) => void
  onKeyDown?: (e: React.KeyboardEvent) => void
  gridCols: string
}

function formatFileSize(bytes: number | null): string {
  if (bytes === null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

export function FileRow({
  item,
  rowIndex,
  isFocused,
  isSelected,
  isPending,
  onClick,
  onDoubleClick,
  onKeyDown,
  gridCols,
}: Props) {
  const dragData = useDragPayload(item.id, item.parentId)
  const draggable = useDraggable({
    id: `${DRAGGABLE_ROW_PREFIX}${item.id}`,
    data: dragData,
    disabled: isPending,
  })

  // 폴더 행은 droppable로도 동작 (hook 호출 순서 안정화 위해 항상 호출)
  const droppable = useFolderDroppable(
    item.type === 'folder' ? item.id : '__not_a_target__',
  )
  const isFolderTarget = item.type === 'folder'

  // ref 합치기 (draggable + droppable, 폴더 행만 droppable ref 바인딩)
  const setRef = (el: HTMLElement | null) => {
    draggable.setNodeRef(el)
    if (isFolderTarget) droppable.setNodeRef(el)
  }

  const isDraggingThis = draggable.isDragging
  const Icon = getFileIcon(item)
  const iconColor = getFileIconColor(item)

  // 드래그 중인 폴더 타겟 시각화
  const dropClass =
    isFolderTarget && droppable.isDragging
      ? droppable.isInvalid || droppable.isSameFolder
        ? 'opacity-50'
        : droppable.isOver
          ? 'bg-accent-soft ring-2 ring-accent'
          : ''
      : ''

  // 상태별 배경 — pending > dragging > selected > hover
  const stateClass = isPending
    ? 'opacity-55 cursor-not-allowed'
    : isDraggingThis
      ? 'opacity-40 cursor-grabbing'
      : isSelected
        ? 'bg-accent-soft hover:bg-[color-mix(in_oklch,var(--accent)_22%,transparent)] cursor-default'
        : 'hover:bg-surface-2 cursor-default'

  return (
    <div
      ref={setRef}
      {...draggable.attributes}
      {...draggable.listeners}
      role="row"
      aria-rowindex={rowIndex}
      aria-selected={isPending ? false : isSelected}
      aria-disabled={isPending || undefined}
      aria-dropeffect={
        isFolderTarget && droppable.isDragging && !droppable.isInvalid && !droppable.isSameFolder
          ? 'move'
          : undefined
      }
      tabIndex={isFocused ? 0 : -1}
      className={`${gridCols} min-h-[var(--row-h)] h-9 select-none border-b border-transparent text-[13px] text-fg transition-colors ${stateClass} ${dropClass}`}
      onClick={(e) => {
        if (isPending) return
        onClick?.(item, e)
      }}
      onDoubleClick={() => {
        if (isPending) return
        onDoubleClick?.(item)
      }}
      onKeyDown={onKeyDown}
      data-file-id={item.id}
    >
      <span className="flex items-center justify-center" role="gridcell" aria-hidden="true">
        <Icon size={16} className={iconColor} strokeWidth={1.6} />
      </span>
      <span
        className="truncate font-medium text-fg"
        role="gridcell"
      >
        {item.name}
      </span>
      <span className="text-right text-[12.5px] text-fg-muted tabular-nums" role="gridcell">
        {formatFileSize(item.size)}
      </span>
      <span className="text-right text-[12.5px] text-fg-muted tabular-nums" role="gridcell">
        {formatDate(item.updatedAt)}
      </span>
      <span
        className="text-right text-[12.5px] text-fg-2 truncate flex items-center justify-end gap-1"
        role="gridcell"
      >
        {isPending && (
          <span
            aria-hidden="true"
            className="inline-block w-3 h-3 border-2 border-surface-3 border-t-fg-muted rounded-full animate-spin"
          />
        )}
        <span className="truncate">{item.updatedBy}</span>
      </span>
    </div>
  )
}
