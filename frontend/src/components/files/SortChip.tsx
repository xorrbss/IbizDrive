'use client'
import { ArrowUp, ArrowDown } from 'lucide-react'
import { useSortParams } from '@/hooks/useSortParams'
import { useSetSortParams } from '@/hooks/useSetSortParams'
import type { SortKey } from '@/types/file'

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: 'name', label: '이름' },
  { value: 'updatedAt', label: '수정일' },
  { value: 'size', label: '크기' },
]

export function SortChip() {
  const { sort, dir } = useSortParams()
  const setSort = useSetSortParams()

  return (
    <div className="inline-flex items-center gap-1 px-2.5 h-7 border border-border-strong rounded-md text-fg-2 text-[12px]">
      <span className="text-fg-muted">정렬</span>
      <select
        aria-label="정렬 기준"
        value={sort}
        onChange={(e) => setSort(e.target.value as SortKey, dir)}
        className="bg-transparent border-0 outline-none px-1 text-fg font-medium cursor-default"
      >
        {SORT_OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      <button
        type="button"
        aria-label={dir === 'asc' ? '내림차순으로 변경' : '오름차순으로 변경'}
        onClick={() => setSort(sort, dir === 'asc' ? 'desc' : 'asc')}
        className="inline-flex items-center justify-center w-5 h-5 rounded text-fg-muted hover:bg-surface-2 hover:text-fg"
      >
        {dir === 'asc' ? (
          <ArrowUp size={12} strokeWidth={1.8} />
        ) : (
          <ArrowDown size={12} strokeWidth={1.8} />
        )}
      </button>
    </div>
  )
}
