'use client'
import { getFileIcon, getFileIconColor } from '@/lib/fileIcons'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  isFocused: boolean
  isSelected: boolean
  isPending: boolean
  onClick?: (item: FileItem, e: React.MouseEvent) => void
  onDoubleClick?: (item: FileItem) => void
  onKeyDown?: (e: React.KeyboardEvent) => void
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

export function FileCard({
  item,
  isFocused,
  isSelected,
  isPending,
  onClick,
  onDoubleClick,
  onKeyDown,
}: Props) {
  const Icon = getFileIcon(item)
  const iconColor = getFileIconColor(item)

  const stateClass = isPending
    ? 'opacity-55 cursor-not-allowed'
    : isSelected
      ? 'border-accent ring-1 ring-accent cursor-default'
      : 'border-border hover:border-border-strong hover:shadow-sm cursor-default'

  return (
    <div
      role="gridcell"
      aria-selected={isPending ? false : isSelected}
      aria-disabled={isPending || undefined}
      aria-label={item.name}
      tabIndex={isFocused ? 0 : -1}
      data-file-id={item.id}
      onClick={(e) => {
        if (isPending) return
        onClick?.(item, e)
      }}
      onDoubleClick={() => {
        if (isPending) return
        onDoubleClick?.(item)
      }}
      onKeyDown={onKeyDown}
      className={`flex flex-col rounded-lg border bg-surface-1 overflow-hidden transition-colors outline-none ${stateClass} ${
        isFocused ? 'ring-2 ring-offset-1 ring-accent' : ''
      }`}
    >
      <div className="aspect-[4/3] bg-surface-2 flex items-center justify-center">
        <Icon size={44} className={iconColor} strokeWidth={1.4} aria-hidden />
      </div>
      <div className="px-2.5 py-2 flex flex-col gap-0.5 min-w-0">
        <span className="text-[12.5px] font-medium text-fg truncate" title={item.name}>
          {item.name}
        </span>
        <span className="text-[11px] text-fg-muted tabular-nums">
          {formatDate(item.updatedAt)}
        </span>
      </div>
    </div>
  )
}
