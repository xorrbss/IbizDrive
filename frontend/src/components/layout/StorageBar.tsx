'use client'
import { useStorageQuota } from '@/hooks/useStorageQuota'

function formatGB(bytes: number): string {
  return `${(bytes / 1024 / 1024 / 1024).toFixed(0)} GB`
}

export function StorageBar() {
  const { data, isLoading } = useStorageQuota()

  if (isLoading || !data) {
    return (
      <div
        aria-label="저장공간 로딩 중"
        className="mt-auto px-2 pt-3 pb-1 border-t border-border text-[11.5px]"
      >
        <div className="h-3 w-20 bg-surface-2 rounded animate-pulse mb-2" />
        <div className="h-1 w-full bg-surface-3 rounded" />
      </div>
    )
  }

  const pct = Math.min(100, Math.round((data.usedBytes / data.totalBytes) * 100))
  const fillColor = pct >= 100 ? 'bg-danger' : pct >= 90 ? 'bg-warn' : 'bg-accent'
  const numColor = pct >= 100 ? 'text-danger' : pct >= 90 ? 'text-warn' : 'text-accent'

  return (
    <section
      aria-label="저장공간"
      className="mt-auto px-2 pt-3 pb-1 border-t border-border text-[11.5px]"
    >
      <div className="flex justify-between text-fg-2 mb-1.5">
        <span>사용량</span>
        <span
          className={`font-semibold tabular-nums ${numColor}`}
          aria-label={`${pct} 퍼센트 사용 중`}
        >
          {pct}%
        </span>
      </div>
      <div
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        className="h-1 bg-surface-3 rounded overflow-hidden"
      >
        <div
          className={`h-full ${fillColor} transition-[width] duration-300`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <div className="text-fg-subtle text-[11px] mt-1 tabular-nums">
        {formatGB(data.usedBytes)} / {formatGB(data.totalBytes)}
      </div>
      <button
        type="button"
        className="mt-2 inline-flex items-center justify-center w-full h-6 rounded border border-border-strong bg-surface-1 text-[11.5px] text-fg-2 hover:bg-surface-2 hover:text-fg"
      >
        용량 업그레이드
      </button>
    </section>
  )
}
