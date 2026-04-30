'use client'
import { fileIconFor } from '@/lib/fileIcon'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  isFocused: boolean
  isSelected: boolean
  isPending: boolean
  onClick?: (item: FileItem, e: React.MouseEvent) => void
  onDoubleClick?: (item: FileItem) => void
}

/**
 * Grid 모드 단일 카드 (M16 docs/01 §18 row 16).
 *
 * FileRow(list) 대비 단순화 — 가상화/DnD/키보드 wrap 없음 (MVP).
 * selection/click/double-click은 FileRow와 동일 패턴 (KISS, useSelectionStore 그대로).
 */
export function FileCard({
  item,
  isFocused,
  isSelected,
  isPending,
  onClick,
  onDoubleClick,
}: Props) {
  const { Icon, className: iconColor } = fileIconFor(item)

  const stateClass = isPending
    ? 'opacity-55 cursor-not-allowed'
    : isSelected
      ? 'bg-accent-soft ring-2 ring-accent cursor-default'
      : 'hover:bg-surface-2 cursor-default'

  return (
    <div
      role="gridcell"
      tabIndex={isFocused ? 0 : -1}
      aria-selected={isPending ? false : isSelected}
      aria-disabled={isPending || undefined}
      data-file-id={item.id}
      onClick={(e) => {
        if (isPending) return
        onClick?.(item, e)
      }}
      onDoubleClick={() => {
        if (isPending) return
        onDoubleClick?.(item)
      }}
      className={`select-none rounded-md border border-border ${stateClass} flex flex-col items-center justify-center p-3 text-center transition-colors`}
    >
      <Icon size={36} className={iconColor} aria-hidden />
      <div
        className="mt-2 text-[12.5px] text-fg font-medium line-clamp-2 break-all w-full"
        title={item.name}
      >
        {item.name}
      </div>
      <div className="mt-1 text-[11px] text-fg-muted truncate w-full">
        {item.type === 'folder' ? '폴더' : formatSize(item.size)}
      </div>
    </div>
  )
}

function formatSize(bytes: number | null): string {
  if (bytes === null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
