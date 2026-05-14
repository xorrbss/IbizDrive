'use client'
import { useState } from 'react'
import { useFileFiltersStore } from '@/stores/fileFilters'
import { isAnyFilterActive } from '@/lib/fileFilters'
import { FilterPopover } from './FilterPopover'

/**
 * Toolbar 진입 버튼 — 필터 popover 토글. 활성 필터 N건 시 chip count 표시.
 */
export function FilterButton() {
  const [open, setOpen] = useState(false)
  const filters = useFileFiltersStore((s) => s.filters)
  const active = isAnyFilterActive(filters)
  const count =
    filters.kinds.length +
    (filters.modified !== 'any' ? 1 : 0) +
    (filters.starred ? 1 : 0) +
    (filters.shared ? 1 : 0)

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="dialog"
        aria-expanded={open}
        className={`h-8 px-2 rounded border text-[12.5px] flex items-center gap-1 ${
          active
            ? 'border-accent text-fg bg-accent/10'
            : 'border-border text-fg-2 hover:bg-surface-2 hover:text-fg'
        }`}
      >
        <span>필터</span>
        {active && (
          <span className="text-[11px] px-1.5 rounded bg-accent text-white">{count}</span>
        )}
      </button>
      {open && <FilterPopover onClose={() => setOpen(false)} />}
    </div>
  )
}
