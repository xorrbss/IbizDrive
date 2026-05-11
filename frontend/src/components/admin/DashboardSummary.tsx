'use client'
import { useAdminDashboardSummary } from '@/hooks/useAdminDashboardSummary'
import { DashboardKpiCard } from './DashboardKpiCard'
import { formatBytes } from '@/lib/formatBytes'

/**
 * Admin 대시보드 KPI 그리드 (admin-dashboard 트랙).
 *
 * <p>{@link useAdminDashboardSummary} 단일 호출 → 8개 카드. 로딩/에러는 한 번만 표시
 * (개별 카드 skeleton은 KISS 위배 — KPI 8개 동시 도착 단일 endpoint).
 *
 * <p>카드 매핑(라벨 → 데이터):
 * <ul>
 *   <li>등록 사용자 → users.total (sub: "활성 active/total")</li>
 *   <li>활성 사용자 → users.active</li>
 *   <li>부서 → departments.total (sub: "활성 active/total" — Department는 is_active 컬럼 부재로 동일치)</li>
 *   <li>활성 폴더 → folders.active</li>
 *   <li>활성 파일 → files.active</li>
 *   <li>휴지통 파일 → files.trashed</li>
 *   <li>24시간 감사 이벤트 → audit.last24h</li>
 *   <li>스토리지 사용량 → formatBytes(storage.usedBytes) — 모든 file_versions sizeBytes 합</li>
 * </ul>
 */
export function DashboardSummary() {
  const { data, isLoading, isError } = useAdminDashboardSummary()

  if (isLoading) {
    return <div className="text-[13px] text-fg-2">불러오는 중…</div>
  }
  if (isError || !data) {
    return (
      <div role="alert" className="text-[13px] text-danger">
        대시보드를 불러오지 못했습니다.
      </div>
    )
  }

  return (
    <div className="kpi-row">
      <DashboardKpiCard
        label="등록 사용자"
        value={data.users.total}
        sub={`활성 ${data.users.active}/${data.users.total}`}
      />
      <DashboardKpiCard label="활성 사용자" value={data.users.active} />
      <DashboardKpiCard
        label="부서"
        value={data.departments.total}
        sub={`활성 ${data.departments.active}/${data.departments.total}`}
      />
      <DashboardKpiCard label="활성 폴더" value={data.folders.active} />
      <DashboardKpiCard label="활성 파일" value={data.files.active} />
      <DashboardKpiCard label="휴지통 파일" value={data.files.trashed} />
      <DashboardKpiCard label="24시간 감사 이벤트" value={data.audit.last24h} />
      <DashboardKpiCard
        label="스토리지 사용량"
        value={formatBytes(data.storage.usedBytes)}
        sub="모든 버전 누적 합"
      />
    </div>
  )
}
