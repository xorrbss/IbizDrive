// src/components/files/FileTableSkeleton.tsx
// 디자인 핸드오프 G4 — 6열 grid (체크박스 / 이름 / 크기 / 수정일 / 수정자 / 액션).
// FileTable GRID_COLS 와 동기 — `36px 1fr 140px 130px 90px 44px`.
export function FileTableSkeleton() {
  return (
    <div className="flex-1 flex flex-col min-h-0" role="status" aria-label="파일 목록 로딩 중">
      <div className="h-[30px] bg-surface-1 border-y border-border" aria-hidden />
      <div className="flex-1 overflow-hidden px-4 py-2 space-y-2">
        {Array.from({ length: 8 }).map((_, i) => (
          <div
            key={i}
            className="grid grid-cols-[36px_1fr_140px_130px_90px_44px] gap-3 items-center h-[var(--row-h)] animate-pulse"
            aria-hidden
          >
            <div className="w-4 h-4 bg-surface-2 rounded" />
            <div className="h-3.5 bg-surface-2 rounded w-3/4" />
            <div className="h-3.5 bg-surface-2 rounded justify-self-end w-full max-w-[120px]" />
            <div className="h-3.5 bg-surface-2 rounded justify-self-end w-full max-w-[110px]" />
            <div className="h-3.5 bg-surface-2 rounded justify-self-end w-full max-w-[70px]" />
            <div className="h-7 w-7 bg-surface-2 rounded justify-self-center" />
          </div>
        ))}
      </div>
    </div>
  )
}
