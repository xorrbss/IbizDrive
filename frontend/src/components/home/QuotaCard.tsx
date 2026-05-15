'use client'
import { DashboardCard } from './DashboardCard'
import { useStorageQuota } from '@/hooks/useStorageQuota'
import { formatBytes } from '@/lib/formatBytes'

/**
 * User Home Dashboard ③ — 내 저장공간 사용량.
 *
 * <p>{@code useStorageQuota} 응답 (`usedBytes/totalBytes`) 기반 progress bar + ratio.
 * 80% 이상 amber, 95% 이상 red.
 */
export function QuotaCard() {
  const { data, isLoading, isError } = useStorageQuota()
  const used = data?.usedBytes ?? 0
  const total = data?.totalBytes ?? 0
  const ratio = total > 0 ? Math.min(1, used / total) : 0
  const percent = Math.round(ratio * 100)
  const tone = ratio >= 0.95 ? 'bg-red-500' : ratio >= 0.8 ? 'bg-amber-500' : 'bg-accent'
  const remaining = Math.max(0, total - used)

  return (
    <DashboardCard title="내 저장공간" subtitle="할당량 대비 사용량">
      {isLoading && <div className="text-[13px] text-fg-muted">불러오는 중…</div>}
      {isError && <div className="text-[13px] text-fg-muted">사용량을 불러올 수 없습니다.</div>}
      {!isLoading && !isError && (
        <div className="space-y-3">
          <div className="text-[14px]">
            <span className="font-semibold">{formatBytes(used)}</span>
            <span className="text-fg-2"> / {total > 0 ? formatBytes(total) : '할당량 미설정'}</span>
          </div>
          <div
            className="h-2 rounded bg-surface-2 overflow-hidden"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percent}
          >
            <div className={`h-full ${tone}`} style={{ width: `${percent}%` }} />
          </div>
          {total > 0 && (
            <p className="text-[12px] text-fg-muted">{formatBytes(remaining)} 남음</p>
          )}
        </div>
      )}
    </DashboardCard>
  )
}
