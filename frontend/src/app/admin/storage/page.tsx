'use client'
import { useAdminStorageOverview } from '@/hooks/useAdminStorageOverview'
import { StorageOverviewCards } from '@/components/admin/StorageOverviewCards'
import { StorageOverviewTable } from '@/components/admin/StorageOverviewTable'

/**
 * /admin/storage — 시스템 스토리지 합계 + 정리 기록 (admin-storage-overview, docs/04 §스토리지).
 *
 * <p>읽기 전용 단일 페이지 — page/size 없음. KPI 카드 5장 + orphan-cleanup 표.
 * 권한 가드는 backend {@code @PreAuthorize("hasRole('ADMIN')")}가 진실, UI는 UX용.
 * 401/403 시 retry false로 즉시 에러 노출.
 */
export default function AdminStoragePage() {
  const { data, isLoading, isError } = useAdminStorageOverview()

  return (
    <div className="flex-1 overflow-auto p-6 space-y-6">
      <header>
        <h1 className="text-lg font-semibold">스토리지</h1>
        <p className="text-[12px] text-fg-2 mt-1">
          시스템 전체 파일/버전/휴지통 합계와 고아 객체 정리 기록을 보여줍니다.
        </p>
      </header>

      {isLoading && <p className="text-sm text-fg-2">불러오는 중…</p>}

      {isError && (
        <p role="alert" className="text-sm text-red-600">
          스토리지 정보를 불러오지 못했습니다.
        </p>
      )}

      {data && (
        <>
          <StorageOverviewCards overview={data.overview} />
          <StorageOverviewTable orphanCleanup={data.overview.orphanCleanup} />
        </>
      )}
    </div>
  )
}
