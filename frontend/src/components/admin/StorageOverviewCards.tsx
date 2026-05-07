import { formatBytes } from '@/lib/formatBytes'
import type { AdminStorageOverview } from '@/types/admin-storage'

/**
 * StorageOverviewCards — 5장 KPI 카드 (admin-storage-overview).
 *
 * <p>presentational — overview 한 객체만 받고 내부 fetch 없음. byte 값은
 * {@link formatBytes}, count는 ko-KR locale.
 */
export function StorageOverviewCards({ overview }: { overview: AdminStorageOverview }) {
  const cards: { label: string; value: string }[] = [
    { label: '전체 파일', value: overview.totalFiles.toLocaleString('ko-KR') },
    { label: '총 버전', value: overview.totalVersions.toLocaleString('ko-KR') },
    { label: '총 크기', value: formatBytes(overview.totalBytes) },
    { label: '휴지통 파일', value: overview.trashedFiles.toLocaleString('ko-KR') },
    { label: '휴지통 크기', value: formatBytes(overview.trashedBytes) },
  ]
  return (
    <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
      {cards.map((c) => (
        <li
          key={c.label}
          className="rounded-md border border-border bg-surface-1 p-4 flex flex-col gap-1"
        >
          <span className="text-[12px] text-fg-2">{c.label}</span>
          <span className="text-[20px] font-semibold tabular-nums">{c.value}</span>
        </li>
      ))}
    </ul>
  )
}
