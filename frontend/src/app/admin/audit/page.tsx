'use client'
import { useMemo, useState } from 'react'
import { Download } from 'lucide-react'
import { useAuditLogs } from '@/hooks/useAuditLogs'
import { AuditFilters } from '@/components/audit/AuditFilters'
import { AuditStream } from '@/components/audit/AuditStream'
import { AuditPagination } from '@/components/audit/AuditPagination'
import { SeverityTabs } from '@/components/audit/SeverityTabs'
import { SectionCard } from '@/components/admin/SectionCard'
import { api } from '@/lib/api'
import { type SeverityFilter } from '@/lib/admin/auditSeverity'
import type { AuditLogFilters } from '@/types/audit'

const PAGE_SIZE = 20
const EMPTY_FILTERS: AuditLogFilters = {}

/**
 * /admin/audit/logs — 감사 로그 페이지 (M12, A2.6 wired, Wave 1 T2 server-side export,
 * audit-export-json 트랙으로 format 분기 / design fidelity sweep Phase 3d).
 *
 * <p>구성 (위에서 아래로):
 * <ul>
 *   <li>SeverityTabs (전체/긴급/주의/정보) — frontend severity 매핑 기반 필터 + CSV/JSON
 *     export 우측 슬롯 — design admin.jsx §audit-header (L737~744)</li>
 *   <li>AuditFilters (행위자/이벤트/날짜) — 기존 유지</li>
 *   <li>AuditStream (audit-row stream 시각) — 기존 AuditTable 을 design audit-stream
 *     형태로 교체</li>
 *   <li>AuditPagination — 기존 유지</li>
 * </ul>
 *
 * <p>backend 연결: `api.getAuditLogs` 는 `GET /api/admin/audit`(A2.6) 직접 호출 —
 * mock 아님. audit_log emission 은 A2/A10/A12 누적 활성. severity 컬럼은 backend
 * 미존재이므로 `auditSeverity.severityOf` 로 frontend 매핑 (v1.x++ backend 합류 시
 * entry 가 직접 severity 노출하면 우선).
 *
 * <p>CSV/JSON export 는 `GET /api/admin/audit/export?format=csv|json`. 현재 필터를
 * 그대로 query string 으로 전달, 브라우저 anchor download 로 트리거. severity 필터는
 * frontend-only 분류이므로 export 에는 포함하지 않는다 (backend severity 컬럼 부재).
 *
 * <p>권한 가드는 backend `@PreAuthorize(AUDITOR or ADMIN)` 가 단독 책임 — MEMBER 는
 * 403 을 받게 되며 UX 는 layout AdminGuard(ADMIN+AUDITOR) 가 담당.
 */
export default function AuditLogsPage() {
  const [filters, setFilters] = useState<AuditLogFilters>(EMPTY_FILTERS)
  const [severity, setSeverity] = useState<SeverityFilter>('all')
  const [page, setPage] = useState(1)
  const { data, isLoading, isError } = useAuditLogs(filters, page, PAGE_SIZE)

  /**
   * severity 필터는 응답된 현재 페이지 entries 를 한 번 더 좁힌다. backend total 은
   * 그대로 유지하여 backend pagination 과 분리한다 (severity-aware count endpoint 는
   * v1.x++; 일반적으로 사용자는 페이지 범위 내에서 severity 분류를 본다).
   */
  const allEntries = useMemo(() => data?.entries ?? [], [data?.entries])
  const visibleEntries = useMemo(() => {
    if (severity === 'all') return allEntries
    return allEntries.filter((e) => e.severity === severity)
  }, [allEntries, severity])

  const handleFiltersChange = (next: AuditLogFilters) => {
    setFilters(next)
    setPage(1)
  }

  const handleReset = () => {
    setFilters(EMPTY_FILTERS)
    setSeverity('all')
    setPage(1)
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-border">
        <h1 className="text-[14px] font-semibold text-fg">감사 로그</h1>
      </div>

      <div className="admin-grid" style={{ padding: '12px 16px' }}>
        <SeverityTabs
          active={severity}
          onChange={setSeverity}
          entries={allEntries}
          right={
            <>
              <a
                href={api.getAuditLogsExportUrl(filters, 'csv')}
                download=""
                className="btn-ghost btn-xs"
                data-testid="audit-export-link-csv"
              >
                <Download size={11} aria-hidden="true" />
                <span style={{ marginLeft: 4 }}>CSV 내보내기</span>
              </a>
              <a
                href={api.getAuditLogsExportUrl(filters, 'json')}
                download=""
                className="btn-ghost btn-xs"
                data-testid="audit-export-link-json"
              >
                <Download size={11} aria-hidden="true" />
                <span style={{ marginLeft: 4 }}>JSON 내보내기</span>
              </a>
            </>
          }
        />

        <SectionCard
          title="이벤트 스트림"
          subtitle={`${visibleEntries.length}건 / ${allEntries.length}건 표시`}
        >
          <AuditFilters value={filters} onChange={handleFiltersChange} onReset={handleReset} />
          <AuditStream
            entries={visibleEntries}
            isLoading={isLoading}
            isError={isError}
          />
        </SectionCard>
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
