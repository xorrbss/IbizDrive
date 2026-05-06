'use client'
import { useState } from 'react'
import { useAuditLogs } from '@/hooks/useAuditLogs'
import { AuditFilters } from '@/components/audit/AuditFilters'
import { AuditTable } from '@/components/audit/AuditTable'
import { AuditPagination } from '@/components/audit/AuditPagination'
import { api } from '@/lib/api'
import type { AuditLogFilters } from '@/types/audit'

const PAGE_SIZE = 20
const EMPTY_FILTERS: AuditLogFilters = {}

/**
 * /admin/audit/logs — 감사 로그 페이지 (M12, A2.6 wired, Wave 1 — T2 server-side export).
 *
 * - 필터(행위자/이벤트/날짜) + 페이지네이션 + CSV export (server-side full-result)
 * - 백엔드 연결: `api.getAuditLogs`는 `GET /api/admin/audit`(A2.6) 직접 호출.
 *   audit_log emission은 A2/A10/A12 누적 활성(file/folder/permission/share).
 * - CSV export는 `GET /api/admin/audit/export` (T2 신설). 현재 필터를 그대로 query string으로 전달,
 *   브라우저 anchor download로 트리거. 권한 가드는 backend `@PreAuthorize(AUDITOR or ADMIN)`이
 *   단독 책임 — MEMBER는 403을 받게 되며 UX는 추후 강화.
 */
export default function AuditLogsPage() {
  const [filters, setFilters] = useState<AuditLogFilters>(EMPTY_FILTERS)
  const [page, setPage] = useState(1)
  const { data, isLoading, isError } = useAuditLogs(filters, page, PAGE_SIZE)

  const handleFiltersChange = (next: AuditLogFilters) => {
    setFilters(next)
    setPage(1) // 필터 변경 시 첫 페이지로
  }

  const handleReset = () => {
    setFilters(EMPTY_FILTERS)
    setPage(1)
  }

  const exportUrl = api.getAuditLogsExportUrl(filters)

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-border">
        <h1 className="text-[14px] font-semibold text-fg">감사 로그</h1>
        <a
          href={exportUrl}
          // download 속성은 same-origin이면 Content-Disposition filename을 우선시한다.
          // 빈 값으로 두어 backend가 지정한 audit_logs_<date>.csv가 그대로 사용되도록.
          download=""
          className="h-8 px-3 inline-flex items-center rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90"
          data-testid="audit-export-link"
        >
          CSV 내보내기
        </a>
      </div>
      <AuditFilters value={filters} onChange={handleFiltersChange} onReset={handleReset} />
      <div className="flex-1 min-h-0">
        <AuditTable
          entries={data?.entries ?? []}
          isLoading={isLoading}
          isError={isError}
        />
      </div>
      <AuditPagination
        page={page}
        pageSize={PAGE_SIZE}
        total={data?.total ?? 0}
        onPageChange={setPage}
      />
    </div>
  )
}
