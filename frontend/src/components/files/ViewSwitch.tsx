'use client'
import { LayoutGrid, List } from 'lucide-react'
import { useViewParam, type ViewMode } from '@/hooks/useViewParam'

/**
 * List/Grid 토글 (M15 docs/01 §18 row 15).
 *
 * URL `?view=` 진실 출처. Grid 본체는 M16 — M15 시점엔 토글 UI/state만 동작.
 */
export function ViewSwitch() {
  const { view, setView } = useViewParam()

  const opt = (mode: ViewMode, label: string, Icon: typeof List) => {
    const active = view === mode
    return (
      <button
        type="button"
        onClick={() => setView(mode)}
        aria-pressed={active}
        aria-label={`${label} 뷰`}
        title={`${label} 뷰`}
        className={`inline-flex items-center justify-center w-7 h-7 rounded ${
          active
            ? 'bg-surface-2 text-fg'
            : 'text-fg-muted hover:bg-surface-2 hover:text-fg'
        }`}
      >
        <Icon size={14} />
      </button>
    )
  }

  return (
    <div
      role="group"
      aria-label="뷰 전환"
      className="inline-flex items-center gap-0.5 p-0.5 rounded border border-border bg-surface-1"
    >
      {opt('list', '목록', List)}
      {opt('grid', '그리드', LayoutGrid)}
    </div>
  )
}
