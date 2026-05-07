'use client'
import { HardDrive } from 'lucide-react'
import { useStorageQuota } from '@/hooks/useStorageQuota'
import { formatBytes } from '@/lib/formatBytes'

/**
 * 저장 용량 바 (M15 docs/01 §18 row 15).
 *
 * Sidebar 하단에 마운트. used/total + 진행 바.
 * 80% 이상이면 warn 색, 95% 이상이면 danger 색.
 */
export function StorageBar() {
  const { data, isLoading } = useStorageQuota()

  if (isLoading || !data) {
    return (
      <div
        aria-label="저장 용량 로딩"
        className="px-2 py-2 text-[11.5px] text-fg-muted animate-pulse"
      >
        <div className="h-3 bg-surface-2 rounded w-3/4 mb-1.5" />
        <div className="h-1.5 bg-surface-2 rounded" />
      </div>
    )
  }

  const { usedBytes, totalBytes } = data
  const ratio = totalBytes > 0 ? usedBytes / totalBytes : 0
  const pct = Math.min(100, Math.round(ratio * 100))
  const tone =
    ratio >= 0.95 ? 'bg-danger' : ratio >= 0.8 ? 'bg-warn' : 'bg-accent'

  return (
    <div
      aria-label={`저장 용량 ${formatBytes(usedBytes)} / ${formatBytes(totalBytes)} (${pct}%)`}
      className="px-2 py-2 text-[11.5px] text-fg-muted"
    >
      <div className="flex items-center gap-1.5 mb-1">
        <HardDrive size={12} className="text-fg-muted" aria-hidden />
        <span className="tabular-nums">
          {formatBytes(usedBytes)} / {formatBytes(totalBytes)}
        </span>
        <span className="ml-auto tabular-nums">{pct}%</span>
      </div>
      <div
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        className="h-1.5 w-full rounded bg-surface-2 overflow-hidden"
      >
        <div className={`h-full ${tone} transition-all`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
