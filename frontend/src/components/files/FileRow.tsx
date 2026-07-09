// frontend/src/components/files/FileRow.tsx
'use client'
import { useDraggable } from '@dnd-kit/core'
import { Check, Star, Lock, Users } from 'lucide-react'
import { useDragPayload } from '@/hooks/useDragPayload'
import { useFolderDroppable } from '@/components/dnd/useFolderDroppable'
import { DRAGGABLE_ROW_PREFIX } from '@/components/dnd/types'
import { useSelectionStore } from '@/stores/selection'
import { fileIconKind } from '@/lib/fileIcon'
import { FileTypeIcon } from '@/components/icons/FileTypeIcon'
import { FileRowActionMenu } from './FileRowActionMenu'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  rowIndex: number
  folderId: string
  isFocused: boolean
  isSelected: boolean
  isPending: boolean
  /** RightPanel(`?file=`)에 현재 열려있는 행 — 좌측 2px accent inset border (zip styles.css `.tr.opened`). */
  isOpened?: boolean
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
  folderId,
  isFocused,
  isSelected,
  isPending,
  isOpened = false,
  onClick,
  onDoubleClick,
  onKeyDown,
  gridCols,
}: Props) {
  const toggleSelection = useSelectionStore((s) => s.toggle)
  const dragData = useDragPayload(item.id, item.parentId, item.type)
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

  // zip styles.css `.tr` 사양:
  //   - hover: bg-surface-2 (L631)
  //   - selected: bg-accent-soft (L632) + dark theme color-mix accent 18% (L633, .row-selected via globals.css)
  //   - selected:hover: color-mix accent 22% (L634)
  //   - pending: opacity .55 (L638)
  //   - opened (외부 prop): inset 2px 0 0 accent (L635, .row-opened via globals.css)
  // transition은 background .08s (L627) — Tailwind `duration-[80ms]`로 통일.
  const stateClass = isPending
    ? 'opacity-55 cursor-not-allowed'
    : isDraggingThis
      ? 'opacity-40 cursor-grabbing'
      : isSelected
        ? 'row-selected bg-accent-soft hover:bg-[color-mix(in_oklch,var(--accent)_22%,transparent)] cursor-default'
        : 'hover:bg-surface-2 cursor-default'

  const iconKind = fileIconKind(item)
  // folder 는 accent 컬러를 따라간다 (currentColor 상속) — 디자인 zip §FileIcon folder 사양.
  // 그 외 kind 는 자체 brand color SVG 라 className 미적용.
  const iconClassName = iconKind === 'folder' ? 'text-accent' : ''

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
      className={`${gridCols} h-[var(--row-h)] select-none border-b border-transparent text-[13px] text-fg transition-[background-color] duration-[80ms] ${
        isOpened ? 'row-opened' : ''
      } ${stateClass} ${dropClass}`}
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
              ? 'bg-accent border-accent text-accent-fg'
              : 'border-border-strong bg-surface-1 hover:border-fg-muted'
          } ${isPending ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'}`}
        >
          {isSelected && <Check size={12} aria-hidden strokeWidth={3} />}
        </button>
      </span>

      {/* 이름 셀: 아이콘 + 파일명 + 배지(star/lock/share/itemsCount) inline.
          zip components.jsx FileRow td-name 구조 답습. 데이터 미존재(undefined) 시 비표시.
          백엔드 wiring은 v1.x (FileItem.starred/restricted/shareCount/itemsCount 참조). */}
      <span className="flex items-center gap-2 min-w-0" role="gridcell">
        <FileTypeIcon kind={iconKind} size={16} className={`flex-shrink-0 ${iconClassName}`} />
        <span className="truncate font-medium text-fg">{item.name}</span>
        {item.starred && (
          <Star
            size={11}
            className="flex-shrink-0 text-warn fill-warn"
            aria-label="즐겨찾기"
          />
        )}
        {item.restricted && (
          <Lock
            size={11}
            className="flex-shrink-0 text-fg-muted"
            aria-label="권한 제한"
          />
        )}
        {typeof item.shareCount === 'number' && item.shareCount > 1 && (
          <span
            className="flex-shrink-0 inline-flex items-center gap-0.5 text-[10.5px] text-fg-muted border border-border rounded-full px-1.5 py-0 tabular-nums"
            aria-label={`${item.shareCount}명 공유`}
          >
            <Users size={10} aria-hidden />
            <span>{item.shareCount}</span>
          </span>
        )}
        {item.type === 'folder' && typeof item.itemsCount === 'number' && (
          <span
            className="flex-shrink-0 text-[11px] text-fg-subtle tabular-nums"
            aria-label={`항목 ${item.itemsCount}개`}
          >
            {item.itemsCount}개
          </span>
        )}
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

      {/* 액션 메뉴 — G4 follow-up. 5개 액션(다운로드/이동/이름 변경/공유/휴지통) + 권한 게이트. */}
      <span className="flex items-center justify-center" role="gridcell">
        <FileRowActionMenu item={item} folderId={folderId} isPending={isPending} />
      </span>
    </div>
  )
}
