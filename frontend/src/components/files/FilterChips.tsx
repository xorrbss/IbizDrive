'use client'
import { useFileFiltersStore } from '@/stores/fileFilters'
import {
  FILTER_KIND_OPTIONS,
  FILTER_MODIFIED_OPTIONS,
} from '@/types/fileFilters'

/**
 * design-zip components.jsx §FilterChips (L724) 1:1 — 적용된 필터 chip bar + 전체 지우기.
 * 활성 필터 0 시 미렌더 (DOM 차지 0).
 */
export function FilterChips() {
  const filters = useFileFiltersStore((s) => s.filters)
  const toggleKind = useFileFiltersStore((s) => s.toggleKind)
  const setModified = useFileFiltersStore((s) => s.setModified)
  const setStarred = useFileFiltersStore((s) => s.setStarred)
  const setShared = useFileFiltersStore((s) => s.setShared)
  const reset = useFileFiltersStore((s) => s.reset)

  const chips: { key: string; label: string; onRemove: () => void }[] = []
  for (const k of filters.kinds) {
    const meta = FILTER_KIND_OPTIONS.find((x) => x.id === k)
    if (meta) chips.push({ key: `kind-${k}`, label: meta.label, onRemove: () => toggleKind(k) })
  }
  if (filters.modified !== 'any') {
    const meta = FILTER_MODIFIED_OPTIONS.find((x) => x.id === filters.modified)
    chips.push({ key: 'modified', label: meta?.label ?? '', onRemove: () => setModified('any') })
  }
  if (filters.starred) {
    chips.push({ key: 'starred', label: '즐겨찾기', onRemove: () => setStarred(false) })
  }
  if (filters.shared) {
    chips.push({ key: 'shared', label: '공유 항목', onRemove: () => setShared(false) })
  }

  if (chips.length === 0) return null

  return (
    <div
      role="region"
      aria-label="적용된 필터"
      className="flex flex-wrap items-center gap-1.5 px-4 py-1.5 border-b border-border bg-bg"
    >
      {chips.map((c) => (
        <span
          key={c.key}
          className="inline-flex items-center gap-1 text-[12px] px-2 py-0.5 rounded bg-bg-2 text-fg-2"
        >
          <span>{c.label}</span>
          <button
            type="button"
            onClick={c.onRemove}
            aria-label={`${c.label} 제거`}
            className="text-fg-muted hover:text-fg"
          >
            ✕
          </button>
        </span>
      ))}
      <button
        type="button"
        onClick={reset}
        className="text-[12px] text-fg-2 hover:text-fg ml-1"
      >
        전체 지우기
      </button>
    </div>
  )
}
