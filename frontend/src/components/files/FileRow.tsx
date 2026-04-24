// frontend/src/components/files/FileRow.tsx
'use client'
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

function fileIcon(item: FileItem): string {
  if (item.type === 'folder') return '📁'
  if (item.mimeType?.startsWith('image/')) return '🖼️'
  if (item.mimeType?.includes('pdf')) return '📄'
  if (item.mimeType?.includes('spreadsheet') || item.mimeType?.includes('excel')) return '📊'
  if (item.mimeType?.includes('word') || item.mimeType?.includes('document')) return '📝'
  return '📎'
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
  // 상태별 배경 — 디자인 토큰 기반
  // 우선순위: pending > selected > hover
  // focus는 focus-visible 전역 링이 담당 (globals.css)
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
      className={`${gridCols} min-h-[var(--row-h)] h-10 select-none border-b border-transparent text-[13px] text-fg transition-colors ${stateClass}`}
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
      <span className="text-center" role="gridcell" aria-hidden="true">{fileIcon(item)}</span>
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
