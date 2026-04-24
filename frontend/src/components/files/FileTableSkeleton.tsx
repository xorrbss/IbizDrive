// src/components/files/FileTableSkeleton.tsx
export function FileTableSkeleton() {
  return (
    <div className="space-y-2 p-4" role="status" aria-label="파일 목록 로딩 중">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="flex items-center gap-4 h-10 animate-pulse">
          <div className="w-6 h-6 bg-gray-200 rounded" />
          <div className="flex-1 h-4 bg-gray-200 rounded" />
          <div className="w-24 h-4 bg-gray-200 rounded" />
          <div className="w-20 h-4 bg-gray-200 rounded" />
        </div>
      ))}
    </div>
  )
}
