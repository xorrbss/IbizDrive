import type { AdminStorageOrphanCleanupSummary } from '@/types/admin-storage'

/**
 * StorageOverviewTable — orphan-cleanup 마지막 실행 정보 (admin-storage-overview).
 *
 * <p>운영 도구 자체는 별도 페이지에 두고, 본 표는 "언제 / 몇 건" 두 정보만 노출.
 * 값이 없으면 "기록 없음" 단일 행. 시각은 ko-KR locale로 표시.
 */
export function StorageOverviewTable({
  orphanCleanup,
}: {
  orphanCleanup: AdminStorageOrphanCleanupSummary | null
}) {
  return (
    <section
      aria-labelledby="orphan-cleanup-title"
      role="region"
      aria-label="고아 객체 정리 기록"
      className="rounded-md border border-border"
    >
      <header className="px-4 py-3 border-b border-border bg-surface-1">
        <h2 id="orphan-cleanup-title" className="text-sm font-semibold">
          고아 객체 정리
        </h2>
        <p className="text-[12px] text-fg-2 mt-0.5">
          마지막 정리 작업의 실행 시각과 삭제 건수입니다.
        </p>
      </header>
      <table className="w-full text-sm">
        <thead className="text-fg-2">
          <tr>
            <th className="text-left px-4 py-2 font-medium">마지막 실행</th>
            <th className="text-left px-4 py-2 font-medium">삭제 건수</th>
          </tr>
        </thead>
        <tbody>
          {orphanCleanup === null ? (
            <tr className="border-t border-border">
              <td colSpan={2} className="px-4 py-6 text-center text-fg-2">
                기록 없음
              </td>
            </tr>
          ) : (
            <tr className="border-t border-border">
              <td className="px-4 py-2 tabular-nums">
                {formatRunAt(orphanCleanup.lastRunAt)}
              </td>
              <td className="px-4 py-2 tabular-nums">
                {orphanCleanup.lastDeletedCount.toLocaleString('ko-KR')}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </section>
  )
}

function formatRunAt(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
