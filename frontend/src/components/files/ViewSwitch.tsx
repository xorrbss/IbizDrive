'use client'
import { List, LayoutGrid } from 'lucide-react'
import { useViewParam } from '@/hooks/useViewParam'

export function ViewSwitch() {
  const { view, setView } = useViewParam()
  return (
    <div
      role="group"
      aria-label="보기 모드"
      className="inline-flex p-0.5 bg-surface-2 rounded-md gap-0.5"
    >
      <button
        type="button"
        aria-label="목록 보기"
        aria-pressed={view === 'list'}
        onClick={() => setView('list')}
        className={`inline-flex items-center justify-center w-[26px] h-[22px] rounded-[3px] ${
          view === 'list'
            ? 'bg-surface-1 text-fg shadow-sm'
            : 'text-fg-muted hover:text-fg'
        }`}
      >
        <List size={13} strokeWidth={1.6} />
      </button>
      <button
        type="button"
        aria-label="그리드 보기"
        aria-pressed={view === 'grid'}
        onClick={() => setView('grid')}
        className={`inline-flex items-center justify-center w-[26px] h-[22px] rounded-[3px] ${
          view === 'grid'
            ? 'bg-surface-1 text-fg shadow-sm'
            : 'text-fg-muted hover:text-fg'
        }`}
      >
        <LayoutGrid size={13} strokeWidth={1.6} />
      </button>
    </div>
  )
}
