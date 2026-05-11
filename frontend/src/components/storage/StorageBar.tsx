'use client'
import { useStorageQuota } from '@/hooks/useStorageQuota'
import { formatBytes } from '@/lib/formatBytes'

/**
 * 저장 용량 바 (M15 docs/01 §18 row 15).
 *
 * Sidebar 하단에 마운트. zip styles.css `.sidebar-storage` / `.storage-bar` 답습 (design-sweep-phase-2b):
 *  - head: "저장공간" 라벨 + pct% (accent)
 *  - 4px bar
 *  - sub: "used / total 사용"
 *
 * 80% 이상이면 warn 색, 95% 이상이면 danger 색.
 */
export function StorageBar() {
  const { data, isLoading } = useStorageQuota()

  if (isLoading || !data) {
    return (
      <div
        aria-label="저장 용량 로딩"
        className="px-2 pt-3 pb-1 text-[11.5px] text-fg-muted animate-pulse"
      >
        <div className="flex justify-between mb-1.5">
          <div className="h-3 bg-surface-2 rounded w-16" />
          <div className="h-3 bg-surface-2 rounded w-8" />
        </div>
        <div className="h-1 bg-surface-2 rounded" />
        <div className="h-3 bg-surface-2 rounded w-24 mt-1.5" />
      </div>
    )
  }

  const { usedBytes, totalBytes } = data
  const ratio = totalBytes > 0 ? usedBytes / totalBytes : 0
  const pct = Math.min(100, Math.round(ratio * 100))
  const tone =
    ratio >= 0.95 ? 'bg-danger' : ratio >= 0.8 ? 'bg-warn' : 'bg-accent'
  const pctColor =
    ratio >= 0.95 ? 'text-danger' : ratio >= 0.8 ? 'text-warn' : 'text-accent'

  return (
    <div
      aria-label={`저장 용량 ${formatBytes(usedBytes)} / ${formatBytes(totalBytes)} (${pct}%)`}
      className="px-2 pt-3 pb-1 text-[11.5px] text-fg-2"
    >
      <div className="flex justify-between items-baseline mb-1.5">
        <span>저장공간</span>
        <span className={`tabular-nums font-semibold ${pctColor}`}>{pct}%</span>
      </div>
      <div
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        className="h-1 w-full rounded-sm bg-surface-3 overflow-hidden"
      >
        <div className={`h-full ${tone} transition-[width] duration-300`} style={{ width: `${pct}%` }} />
      </div>
      <div className="text-[11px] text-fg-subtle mt-1 tabular-nums">
        {formatBytes(usedBytes)} / {formatBytes(totalBytes)} 사용
      </div>
    </div>
  )
}
