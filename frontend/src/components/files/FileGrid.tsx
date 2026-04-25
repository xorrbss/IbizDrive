'use client'
import type { RefObject } from 'react'
import { FileCard } from './FileCard'
import type { FileItem } from '@/types/file'

type Props = {
  items: FileItem[]
  focusedIndex: number
  selectedIds: Set<string>
  pendingIds: Set<string>
  onClick: (item: FileItem, e: React.MouseEvent) => void
  onDoubleClick: (item: FileItem) => void
  onKeyDown: (e: React.KeyboardEvent) => void
  scrollRef: RefObject<HTMLDivElement | null>
}

/**
 * Grid 모드 — CSS auto-fill grid. 가상화 없음 (M16 MVP 한계).
 * 키보드 nav은 linear (FileTable의 handleKeyDown 그대로 위임).
 *
 * 설계: docs/01 §16 (M16 Grid View)
 */
export function FileGrid({
  items,
  focusedIndex,
  selectedIds,
  pendingIds,
  onClick,
  onDoubleClick,
  onKeyDown,
  scrollRef,
}: Props) {
  return (
    <div
      ref={scrollRef}
      tabIndex={0}
      onKeyDown={onKeyDown}
      role="grid"
      aria-label="파일 목록"
      aria-multiselectable={true}
      className="flex-1 overflow-y-auto outline-none px-4 pt-1 pb-10 grid gap-3 content-start"
      style={{
        gridTemplateColumns: 'repeat(auto-fill, minmax(172px, 1fr))',
      }}
    >
      {items.map((item, idx) => (
        <FileCard
          key={item.id}
          item={item}
          isFocused={focusedIndex === idx}
          isSelected={selectedIds.has(item.id)}
          isPending={pendingIds.has(item.id)}
          onClick={onClick}
          onDoubleClick={onDoubleClick}
          onKeyDown={onKeyDown}
        />
      ))}
    </div>
  )
}
