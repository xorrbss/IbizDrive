'use client'
import { useEffect, useRef } from 'react'
import { useFileFiltersStore } from '@/stores/fileFilters'
import {
  FILTER_KIND_OPTIONS,
  FILTER_MODIFIED_OPTIONS,
  type FileKindId,
  type FileModifiedId,
} from '@/types/fileFilters'

interface FilterPopoverProps {
  onClose: () => void
}

/**
 * design-zip components.jsx §FilterPopover (L637) 1:1 — 4 섹션 popover dialog.
 * owner 섹션은 backend list response `owner.id` 부재로 본 PR 제외 (별도 'shared' 토글로 분리).
 *
 * <p>외부 클릭 / Escape 시 onClose. 즉시 store 갱신 (낙관적 UX) — "적용" 버튼은 단순 close.
 */
export function FilterPopover({ onClose }: FilterPopoverProps) {
  const filters = useFileFiltersStore((s) => s.filters)
  const toggleKind = useFileFiltersStore((s) => s.toggleKind)
  const setModified = useFileFiltersStore((s) => s.setModified)
  const setStarred = useFileFiltersStore((s) => s.setStarred)
  const setShared = useFileFiltersStore((s) => s.setShared)
  const reset = useFileFiltersStore((s) => s.reset)

  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('mousedown', onClick)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('mousedown', onClick)
    }
  }, [onClose])

  return (
    <div
      ref={ref}
      role="dialog"
      aria-label="파일 필터"
      className="absolute right-0 top-full mt-1 z-20 w-[320px] rounded-lg border border-border bg-surface-1 shadow-lg p-3 space-y-3"
    >
      <section>
        <div className="text-[12px] font-semibold text-fg-2 mb-1.5">파일 종류</div>
        <div className="flex flex-wrap gap-1.5">
          {FILTER_KIND_OPTIONS.map((k) => {
            const active = filters.kinds.includes(k.id)
            return (
              <button
                key={k.id}
                type="button"
                onClick={() => toggleKind(k.id as FileKindId)}
                className={`text-[12px] px-2 py-0.5 rounded border ${
                  active
                    ? 'border-accent bg-accent/10 text-fg'
                    : 'border-border bg-bg text-fg-2 hover:text-fg'
                }`}
                aria-pressed={active}
              >
                {active && <span aria-hidden>✓ </span>}
                {k.label}
              </button>
            )
          })}
        </div>
      </section>

      <section>
        <div className="text-[12px] font-semibold text-fg-2 mb-1.5">수정일</div>
        <div className="flex flex-wrap gap-1.5">
          {FILTER_MODIFIED_OPTIONS.map((m) => {
            const active = filters.modified === m.id
            return (
              <label
                key={m.id}
                className={`text-[12px] px-2 py-0.5 rounded border cursor-pointer ${
                  active
                    ? 'border-accent bg-accent/10 text-fg'
                    : 'border-border bg-bg text-fg-2 hover:text-fg'
                }`}
              >
                <input
                  type="radio"
                  name="modified"
                  checked={active}
                  onChange={() => setModified(m.id as FileModifiedId)}
                  className="sr-only"
                />
                {m.label}
              </label>
            )
          })}
        </div>
      </section>

      <section className="flex items-center gap-4">
        <label className="text-[12.5px] text-fg-2 flex items-center gap-1.5">
          <input
            type="checkbox"
            checked={filters.starred}
            onChange={(e) => setStarred(e.target.checked)}
          />
          즐겨찾기만
        </label>
        <label className="text-[12.5px] text-fg-2 flex items-center gap-1.5">
          <input
            type="checkbox"
            checked={filters.shared}
            onChange={(e) => setShared(e.target.checked)}
          />
          공유 항목만
        </label>
      </section>

      <footer className="flex items-center justify-between pt-2 border-t border-border">
        <button
          type="button"
          onClick={reset}
          className="text-[12px] text-fg-2 hover:text-fg"
        >
          초기화
        </button>
        <button
          type="button"
          onClick={onClose}
          className="text-[12px] px-3 py-1 rounded bg-accent text-white"
        >
          적용
        </button>
      </footer>
    </div>
  )
}
