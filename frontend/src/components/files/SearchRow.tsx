'use client'
import { getFileIcon, getFileIconColor } from '@/lib/fileIcons'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  rowIndex: number
  isFocused: boolean
  isSelected: boolean
  isPending: boolean
  parentName: string
  onClick?: (item: FileItem, e: React.MouseEvent) => void
  onDoubleClick?: (item: FileItem) => void
  onKeyDown?: (e: React.KeyboardEvent) => void
  gridCols: string
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

export function SearchRow({
  item,
  rowIndex,
  isFocused,
  isSelected,
  isPending,
  parentName,
  onClick,
  onDoubleClick,
  onKeyDown,
  gridCols,
}: Props) {
  const Icon = getFileIcon(item)
  const iconColor = getFileIconColor(item)

  const stateClass = isPending
    ? 'opacity-55 cursor-not-allowed'
    : isSelected
      ? 'bg-accent-soft hover:bg-[color-mix(in_oklch,var(--accent)_22%,transparent)] cursor-default'
      : 'hover:bg-surface-2 cursor-default'

  return (
    <div
      role="row"
      aria-rowindex={rowIndex}
      aria-selected={isPending ? false : isSelected}
      aria-disabled={isPending || undefined}
      tabIndex={isFocused ? 0 : -1}
      className={`${gridCols} min-h-[var(--row-h)] h-9 select-none border-b border-transparent text-[13px] text-fg transition-colors ${stateClass}`}
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
      <span className="flex items-center justify-center" role="gridcell" aria-hidden>
        <Icon size={16} className={iconColor} strokeWidth={1.6} />
      </span>
      <span className="truncate font-medium text-fg" role="gridcell">
        {item.name}
      </span>
      <span
        className="truncate text-[12.5px] text-fg-muted"
        role="gridcell"
        title={parentName}
      >
        {parentName}
      </span>
      <span
        className="text-right text-[12.5px] text-fg-muted tabular-nums"
        role="gridcell"
      >
        {formatDate(item.updatedAt)}
      </span>
      <span
        className="text-right text-[12.5px] text-fg-2 truncate flex items-center justify-end gap-1"
        role="gridcell"
      >
        {isPending && (
          <span
            aria-hidden
            className="inline-block w-3 h-3 border-2 border-surface-3 border-t-fg-muted rounded-full animate-spin"
          />
        )}
        <span className="truncate">{item.updatedBy}</span>
      </span>
    </div>
  )
}
