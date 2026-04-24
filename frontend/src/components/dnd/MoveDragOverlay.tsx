'use client'

export function MoveDragOverlay({ count }: { count: number }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded bg-accent text-accent-fg text-[12.5px] font-medium shadow-lg pointer-events-none"
    >
      <span aria-hidden>📎</span>
      <span>{count}개 항목 이동 중</span>
    </div>
  )
}
