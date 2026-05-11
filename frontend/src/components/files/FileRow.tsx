// frontend/src/components/files/FileRow.tsx
'use client'
import { useDraggable } from '@dnd-kit/core'
import { MoreHorizontal, Check } from 'lucide-react'
import { useDragPayload } from '@/hooks/useDragPayload'
import { useFolderDroppable } from '@/components/dnd/useFolderDroppable'
import { DRAGGABLE_ROW_PREFIX } from '@/components/dnd/types'
import { useSelectionStore } from '@/stores/selection'
import { fileIconFor } from '@/lib/fileIcon'
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
  const toggleSelection = useSelectionStore((s) => s.toggle)
  const dragData = useDragPayload(item.id, item.parentId)
  const draggable = useDraggable({
    id: `${DRAGGABLE_ROW_PREFIX}${item.id}`,
    data: dragData,
    disabled: isPending,
  })

  const droppable = useFolderDroppable(
    item.type === 'folder' ? item.id : '__not_a_target__',
  )
  const isFolderTarget = item.type === 'folder'

  const setRef = (el: HTMLElement | null) => {
    draggable.setNodeRef(el)
    if (isFolderTarget) droppable.setNodeRef(el)
  }

  const isDraggingThis = draggable.isDragging

  const dropClass =
    isFolderTarget && droppable.isDragging
      ? droppable.isCrossWorkspace || droppable.isSharedTarget
        ? 'opacity-50 cursor-not-allowed'
        : droppable.isInvalid || droppable.isSameFolder
          ? 'opacity-50'
          : droppable.isOver
            ? 'bg-accent-soft ring-2 ring-accent'
            : ''
      : ''

  const dropTitle =
    isFolderTarget && droppable.isDragging
      ? droppable.isCrossWorkspace
        ? '다른 workspace로 이동 불가 (컨텍스트 메뉴를 사용하세요)'
        : droppable.isSharedTarget
          ? '공유받음 영역으로 이동 불가'
          : undefined
      : undefined

  const stateClass = isPending
    ? 'opacity-55 cursor-not-allowed'
    : isDraggingThis
      ? 'opacity-40 cursor-grabbing'
      : isSelected
        ? 'bg-accent-soft hover:bg-[color-mix(in_oklch,var(--accent)_22%,transparent)] cursor-default'
        : 'hover:bg-surface-2 cursor-default'

  const { Icon, className: iconClassName } = fileIconFor(item)

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
      className={`${gridCols} h-[var(--row-h)] select-none border-b border-transparent text-[13px] text-fg transition-colors ${stateClass} ${dropClass}`}
      title={dropTitle}
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
      {/* 체크박스 — M4 selection store 연결. Space/Ctrl+A 등 키보드 토글은 FileTable 레벨에서 처리. */}
      <span className="flex items-center justify-center" role="gridcell">
        <button
          type="button"
          role="checkbox"
          aria-checked={isSelected}
          aria-label={`${item.name} 선택`}
          disabled={isPending}
          tabIndex={-1}
          onClick={(e) => {
            e.stopPropagation()
            if (isPending) return
            toggleSelection(item.id)
          }}
          className={`h-4 w-4 inline-flex items-center justify-center rounded border transition-colors ${
            isSelected
              ? 'bg-accent border-accent text-accent-text'
              : 'border-border-strong bg-surface-1 hover:border-fg-muted'
          } ${isPending ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'}`}
        >
          {isSelected && <Check size={12} aria-hidden strokeWidth={3} />}
        </button>
      </span>

      {/* 이름 셀: 아이콘 + 파일명 inline */}
      <span className="flex items-center gap-2 min-w-0" role="gridcell">
        <Icon size={16} className={`flex-shrink-0 ${iconClassName}`} aria-hidden />
        <span className="truncate font-medium text-fg">{item.name}</span>
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

      {/* 액션 버튼 — 디자인 핸드오프 G4 layout placeholder. 클릭 시 컨텍스트 메뉴(rename/move/share/delete)
          연결은 v1.x 후속 PR. 현재는 propagation만 차단해 row 클릭과 분리. */}
      <span className="flex items-center justify-center" role="gridcell">
        <button
          type="button"
          aria-label="더 보기"
          disabled={isPending}
          tabIndex={-1}
          onClick={(e) => {
            e.stopPropagation()
          }}
          className={`h-7 w-7 inline-flex items-center justify-center rounded text-fg-muted hover:bg-surface-3 hover:text-fg ${
            isPending ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'
          }`}
        >
          <MoreHorizontal size={14} aria-hidden />
        </button>
      </span>
    </div>
  )
}
