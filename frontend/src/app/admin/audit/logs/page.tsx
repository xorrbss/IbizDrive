'use client'
import { useState } from 'react'
import { toast } from 'sonner'
import { useAuditLogs } from '@/hooks/useAuditLogs'
import { AuditFilters } from '@/components/audit/AuditFilters'
import { AuditTable } from '@/components/audit/AuditTable'
import { AuditPagination } from '@/components/audit/AuditPagination'
import { toAuditCsvBlob } from '@/lib/auditCsv'
import type { AuditLogFilters } from '@/types/audit'

const PAGE_SIZE = 20
const EMPTY_FILTERS: AuditLogFilters = {}

/**
 * /admin/audit/logs — 감사 로그 페이지 (M12, A2.6 wired, docs/04 §7).
 *
 * - 필터 (행위자/이벤트/날짜) + 페이지네이션 + CSV export
 * - 백엔드 연결: `api.getAuditLogs`는 `GET /api/admin/audit`(A2.6) 직접 호출.
 *   audit_log emission은 A2/A10/A12 누적 활성(file/folder/permission/share).
 * - CSV export는 client-side current-page만(`toAuditCsvBlob`). 서버 전체 결과
 *   스트리밍 + `audit.exported` runtime emission은 v1.x deferred.
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

  const handleExport = () => {
    if (!data || data.entries.length === 0) {
      toast.info('내보낼 감사 로그가 없습니다.')
      return
    }
    // mock: 현재 페이지만 export. 백엔드 연결 시 전체 필터 결과를 서버에서 스트리밍.
    const blob = toAuditCsvBlob(data.entries)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit_logs_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    toast.success(`${data.entries.length}건을 CSV로 내보냈습니다`)
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-border">
        <h1 className="text-[14px] font-semibold text-fg">감사 로그</h1>
        <button
          type="button"
          onClick={handleExport}
          disabled={isLoading || !data || data.entries.length === 0}
          className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          CSV 내보내기
        </button>
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
