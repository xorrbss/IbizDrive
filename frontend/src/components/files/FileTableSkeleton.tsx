// src/components/files/FileTableSkeleton.tsx
export function FileTableSkeleton() {
  return (
    <div className="flex-1 flex flex-col min-h-0" role="status" aria-label="파일 목록 로딩 중">
      <div className="h-[30px] bg-surface-1 border-y border-border" aria-hidden />
      <div className="flex-1 overflow-hidden px-4 py-2 space-y-2">
        {Array.from({ length: 8 }).map((_, i) => (
          <div
            key={i}
            className="grid grid-cols-[28px_1fr_110px_130px_90px] gap-3 items-center h-10 animate-pulse"
            aria-hidden
          >
            <div className="w-5 h-5 bg-surface-2 rounded" />
            <div className="h-3.5 bg-surface-2 rounded w-3/4" />
            <div className="h-3.5 bg-surface-2 rounded justify-self-end w-full max-w-[90px]" />
            <div className="h-3.5 bg-surface-2 rounded justify-self-end w-full max-w-[110px]" />
            <div className="h-3.5 bg-surface-2 rounded justify-self-end w-full max-w-[70px]" />
          </div>
        ))}
      </div>
    </div>
  )
}
