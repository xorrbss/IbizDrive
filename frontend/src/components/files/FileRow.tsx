// src/components/files/FileRow.tsx
'use client'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  rowIndex: number         // 1-based for aria-rowindex
  isFocused: boolean
  onClick?: (id: string) => void
  onDoubleClick?: (item: FileItem) => void
  onKeyDown?: (e: React.KeyboardEvent) => void
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
  onClick,
  onDoubleClick,
  onKeyDown,
}: Props) {
  return (
    <div
      role="row"
      aria-rowindex={rowIndex}
      aria-selected={false}
      tabIndex={isFocused ? 0 : -1}
      className={`flex items-center gap-4 h-10 px-4 cursor-pointer select-none border-b border-gray-100 ${
        isFocused ? 'bg-blue-50 outline outline-2 outline-blue-400' : 'hover:bg-gray-50'
      }`}
      onClick={() => onClick?.(item.id)}
      onDoubleClick={() => onDoubleClick?.(item)}
      onKeyDown={onKeyDown}
      data-file-id={item.id}
    >
      <span className="w-6 text-center" role="gridcell" aria-hidden="true">{fileIcon(item)}</span>
      <span className="flex-1 truncate text-sm font-medium" role="gridcell">{item.name}</span>
      <span className="w-24 text-right text-xs text-gray-500" role="gridcell">{formatFileSize(item.size)}</span>
      <span className="w-28 text-right text-xs text-gray-500" role="gridcell">{formatDate(item.updatedAt)}</span>
      <span className="w-20 text-right text-xs text-gray-500 truncate" role="gridcell">{item.updatedBy}</span>
    </div>
  )
}
