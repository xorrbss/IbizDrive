'use client'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useSelectionStore } from '@/stores/selection'

function formatSize(bytes: number): string {
  if (bytes === 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

type Props = {
  folderId: string
}

export function StatusBar({ folderId }: Props) {
  const { sort, dir } = useSortParams()
  const { data: items } = useFilesInFolder(folderId, sort, dir)
  const selectedIds = useSelectionStore((s) => s.ids)

  const itemCount = items?.length ?? 0
  const selectedCount = selectedIds.size

  let selectedSize = 0
  if (items && selectedCount > 0) {
    for (const it of items) {
      if (selectedIds.has(it.id) && it.size != null) {
        selectedSize += it.size
      }
    }
  }

  const left =
    selectedCount > 0
      ? `${itemCount}개 항목 · ${selectedCount}개 선택됨`
      : `${itemCount}개 항목`

  const right = formatSize(selectedSize)

  return (
    <footer
      role="contentinfo"
      aria-label="상태 표시줄"
      className="flex items-center justify-between gap-3 px-4 h-7 border-t border-border bg-surface-1 text-[11.5px] text-fg-muted"
    >
      <span>{left}</span>
      <span className="tabular-nums">{right}</span>
    </footer>
  )
}
