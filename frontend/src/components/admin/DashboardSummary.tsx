'use client'
import {
  Users,
  UserCheck,
  Building2,
  FolderOpen,
  FileText,
  Trash2,
  Activity,
  HardDrive,
} from 'lucide-react'
import { useAdminDashboardSummary } from '@/hooks/useAdminDashboardSummary'
import { DashboardKpiCard } from './DashboardKpiCard'
import { formatBytes } from '@/lib/formatBytes'

/**
 * Admin 대시보드 KPI 그리드 (admin-dashboard 트랙).
 *
 * <p>{@link useAdminDashboardSummary} 단일 호출 → 8개 카드. 로딩/에러는 한 번만 표시
 * (개별 카드 skeleton은 KISS 위배 — KPI 8개 동시 도착 단일 endpoint).
 *
 * <p>카드 매핑(라벨 → 데이터 → 디자인 §KPICard icon prop):
 * <ul>
 *   <li>등록 사용자 → users.total → Users (디자인 "team")</li>
 *   <li>활성 사용자 → users.active → UserCheck</li>
 *   <li>부서 → departments.total → Building2</li>
 *   <li>활성 폴더 → folders.active → FolderOpen</li>
 *   <li>활성 파일 → files.active → FileText (디자인 "folder")</li>
 *   <li>휴지통 파일 → files.trashed → Trash2</li>
 *   <li>24시간 감사 이벤트 → audit.last24h → Activity</li>
 *   <li>스토리지 사용량 → formatBytes(storage.usedBytes) → HardDrive</li>
 * </ul>
 *
 * <p>아이콘 wiring은 디자인 §KPICard L194 `{icon && <UIIcon size={13} />}` 1:1.
 * 디자인은 4 KPI만 제시하지만 본 페이지는 backend 실데이터 8 카드를 유지 (granular
 * 운영 지표 노출이 design 4-카드보다 정보량 우위) — 시각 fidelity만 디자인과 정합.
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

  // Jackson은 null Double을 JSON null로 직렬화 → JS는 null. DashboardKpiCard는 `delta != null`
  // 가드로 null/undefined 양쪽 처리하므로 ?? undefined 변환 불필요.
  return (
    <div className="kpi-row">
      <DashboardKpiCard
        label="등록 사용자"
        value={data.users.total}
        sub={`활성 ${data.users.active}/${data.users.total}`}
        delta={data.users.totalDelta ?? undefined}
        icon={Users}
      />
      <DashboardKpiCard
        label="활성 사용자"
        value={data.users.active}
        delta={data.users.activeDelta ?? undefined}
        icon={UserCheck}
      />
      <DashboardKpiCard
        label="부서"
        value={data.departments.total}
        sub={`활성 ${data.departments.active}/${data.departments.total}`}
        delta={data.departments.totalDelta ?? undefined}
        icon={Building2}
      />
      <DashboardKpiCard
        label="활성 폴더"
        value={data.folders.active}
        delta={data.folders.activeDelta ?? undefined}
        icon={FolderOpen}
      />
      <DashboardKpiCard
        label="활성 파일"
        value={data.files.active}
        delta={data.files.activeDelta ?? undefined}
        icon={FileText}
      />
      <DashboardKpiCard
        label="휴지통 파일"
        value={data.files.trashed}
        delta={data.files.trashedDelta ?? undefined}
        icon={Trash2}
      />
      <DashboardKpiCard
        label="24시간 감사 이벤트"
        value={data.audit.last24h}
        delta={data.audit.last24hDelta ?? undefined}
        icon={Activity}
      />
      <DashboardKpiCard
        label="스토리지 사용량"
        value={formatBytes(data.storage.usedBytes)}
        sub="모든 버전 누적 합"
        delta={data.storage.usedBytesDelta ?? undefined}
        icon={HardDrive}
      />
    </div>
  )
}
