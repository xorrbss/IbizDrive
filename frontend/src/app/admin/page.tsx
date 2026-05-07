import { DashboardSummary } from '@/components/admin/DashboardSummary'
import { AdminGuard } from '@/components/auth/AdminGuard'

/**
 * `/admin` 진입점 — 운영 KPI 대시보드 (admin-dashboard 트랙).
 *
 * <p>v1.x deferred landing(가용 기능 카드 2개 + "추가 예정" 박스)을 KPI 그리드로 교체.
 * 8개 KPI는 backend `GET /api/admin/dashboard/summary` 단일 응답 — 카드별 fetch 분산
 * 안 함 (KISS, 단일 endpoint).
 *
 * <p>가드: ADMIN-only — layout이 ADMIN+AUDITOR 통과(read-only 영역 진입)로 완화
 * 되었으므로 본 페이지는 default `<AdminGuard>`로 다시 좁힌다. AUDITOR가 직접
 * `/admin` 진입 시 /files redirect (wave1.5-auditor-admin-ui-access).
 */
export default function AdminDashboardPage() {
  return (
    <AdminGuard>
      <div className="p-8 max-w-[1200px]">
        <h1 className="text-[20px] font-semibold text-fg mb-1">대시보드</h1>
        <p className="text-[13px] text-fg-2 mb-6">현재 시스템 운영 지표.</p>
        <DashboardSummary />
      </div>
    </AdminGuard>
  )
}
