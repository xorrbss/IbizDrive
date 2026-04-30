'use client'
import { ArrowUpDown, ChevronDown } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useSortParams } from '@/hooks/useSortParams'
import type { SortKey } from '@/types/file'

const LABELS: Record<SortKey, string> = {
  name: '이름',
  updatedAt: '수정일',
  size: '크기',
}

const KEYS: SortKey[] = ['name', 'updatedAt', 'size']

/**
 * 정렬 드롭다운 (M15 docs/01 §18 row 15).
 *
 * 진실 출처는 URL `?sort=&dir=` (docs/01 §1.1) — `useSortParams` 통해서만 읽고 쓴다.
 * 같은 key 재선택은 asc/desc 토글, 다른 key 선택은 asc로 reset.
 */
export function SortChip() {
  const { sort, dir, setSort } = useSortParams()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement | null>(null)

  // outside click → close
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (!ref.current) return
      if (!ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  const dirLabel = dir === 'asc' ? '오름차순' : '내림차순'

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`정렬: ${LABELS[sort]} ${dirLabel}`}
        className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded border border-border bg-surface-1 text-[12px] text-fg hover:bg-surface-2"
      >
        <ArrowUpDown size={12} className="text-fg-muted" />
        <span>{LABELS[sort]}</span>
        <span className="text-fg-muted">{dir === 'asc' ? '↑' : '↓'}</span>
        <ChevronDown size={12} className="text-fg-muted" />
      </button>
      {open && (
        <div
          role="menu"
          aria-label="정렬 옵션"
          className="absolute z-10 mt-1 right-0 min-w-[160px] rounded border border-border bg-surface-1 shadow-md py-1 text-[12.5px]"
        >
          {KEYS.map((k) => {
            const active = k === sort
            return (
              <button
                key={k}
                type="button"
                role="menuitemradio"
                aria-checked={active}
                onClick={() => {
                  setSort(k)
                  setOpen(false)
                }}
                className={`w-full text-left px-3 py-1.5 hover:bg-surface-2 flex items-center justify-between ${
                  active ? 'text-fg font-medium' : 'text-fg-muted'
                }`}
              >
                <span>{LABELS[k]}</span>
                {active && (
                  <span className="text-fg-muted text-[11px]">
                    {dir === 'asc' ? '↑ 오름차순' : '↓ 내림차순'}
                  </span>
                )}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
