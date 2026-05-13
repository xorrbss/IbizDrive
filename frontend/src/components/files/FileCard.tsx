'use client'
import { fileIconKind } from '@/lib/fileIcon'
import { FileTypeIcon } from '@/components/icons/FileTypeIcon'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  isFocused: boolean
  isSelected: boolean
  isPending: boolean
  /** RightPanel(`?file=`)에 현재 열려있는 카드 — selected와 동일한 accent ring (zip `.grid-card.opened` 미정의이나 일관성 위해 동일 시각). */
  isOpened?: boolean
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
  isOpened = false,
  onClick,
  onDoubleClick,
}: Props) {
  const iconKind = fileIconKind(item)
  // folder 는 accent 상속 (FileRow와 동기). 그 외 kind 는 자체 brand color.
  const iconColor = iconKind === 'folder' ? 'text-accent' : ''

  // zip styles.css `.grid-card` 사양:
  //   - hover: border-strong + shadow-sm (L712~714, background 변화 없음)
  //   - selected: border-accent + ring-1 accent (L716~718, "box-shadow: 0 0 0 1px var(--accent)")
  //   - pending: opacity .55 (L720)
  //   - transition: border-color .12s, box-shadow .12s (L710, Tailwind duration-[120ms])
  // selected의 ring은 Tailwind `ring-1` + `ring-accent` 로 표현. ring-2 → ring-1로 정정
  // (이전 구현은 `bg-accent-soft + ring-2` 였으나 zip 사양에는 bg 변화 없고 1px outline).
  const isAccented = isSelected || isOpened
  const stateClass = isPending
    ? 'opacity-55 cursor-not-allowed'
    : isAccented
      ? 'border-accent ring-1 ring-accent cursor-default'
      : 'hover:border-border-strong hover:shadow-sm cursor-default'

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
      className={`select-none rounded-md border border-border bg-surface-1 ${stateClass} flex flex-col items-center justify-center p-3 text-center transition-[border-color,box-shadow] duration-[120ms]`}
    >
      <FileTypeIcon kind={iconKind} size={36} className={iconColor} />
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
