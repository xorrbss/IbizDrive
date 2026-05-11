import Link from 'next/link'
import { DashboardSummary } from '@/components/admin/DashboardSummary'
import { SectionCard } from '@/components/admin/SectionCard'
import { UploadChart } from '@/components/admin/overview/UploadChart'
import { FlagRow } from '@/components/admin/overview/FlagRow'
import { DeptRow } from '@/components/admin/overview/DeptRow'
import { AdminGuard } from '@/components/auth/AdminGuard'
import {
  ADMIN_DEPARTMENTS,
  ADMIN_UPLOADS_28D,
  formatTBGB,
} from '@/lib/admin/overviewMock'
import { ADMIN_FLAGGED } from '@/lib/admin/sharingMock'

/**
 * `/admin` 진입점 — 운영 KPI 대시보드 + overview 위젯 (admin-dashboard / design
 * fidelity sweep Phase 3b 트랙).
 *
 * <p>구성 (위에서 아래로):
 * <ul>
 *   <li>실서비스 KPI 그리드 (`<DashboardSummary>`) — backend wiring 완료</li>
 *   <li>업로드 추이 + 플래그된 공유 (admin-row-2-1) — mock 위젯</li>
 *   <li>부서별 저장공간 (mock 위젯, 좌측 1/2 — 우측은 후속 트랙)</li>
 * </ul>
 *
 * <p>위젯(`UploadChart`, `FlagRow`, `DeptRow`)은 디자인 핸드오프 2026-05-10
 * admin.jsx §AdminOverview (L98~182) 의 시각 fidelity 재현이다. trend / dept-usage
 * backend endpoint 는 v1.x backlog (`docs/v1x-backlog.md`) — 본 페이지 상단의
 * 운영자 안내 callout 으로 mock 상태를 사전 고지한다.
 *
 * <p>가드: ADMIN-only — layout 이 ADMIN+AUDITOR 허용으로 완화되었으므로 본 페이지는
 * default `<AdminGuard>` 로 다시 좁힌다 (wave1.5-auditor-admin-ui-access).
 */
export default function AdminDashboardPage() {
  const uploadsTotal = ADMIN_UPLOADS_28D.reduce((a, b) => a + b, 0)

  return (
    <AdminGuard>
      <div className="admin-grid">
        <div>
          <h1 className="text-[20px] font-semibold text-fg mb-1">대시보드</h1>
          <p className="text-[13px] text-fg-2">현재 시스템 운영 지표.</p>
        </div>

        <DashboardSummary />

        <OverviewMockCallout />

        <div className="admin-row admin-row-2-1">
          <SectionCard
            title="업로드 추이"
            subtitle="최근 28일 · 일별 합계"
            right={<span className="admin-mini-stat">총 {formatTBGB(uploadsTotal)}</span>}
          >
            <UploadChart data={ADMIN_UPLOADS_28D} />
          </SectionCard>

          <SectionCard
            title="플래그된 공유"
            subtitle="자동 탐지 · 검토 대기"
            right={
              <Link
                href="/admin/sharing"
                className="btn-ghost btn-xs"
                style={{ textDecoration: 'none' }}
              >
                전체 보기 →
              </Link>
            }
          >
            <div className="flag-list">
              {ADMIN_FLAGGED.map((f) => (
                <FlagRow key={f.id} item={f} />
              ))}
              {ADMIN_FLAGGED.length === 0 && (
                <div className="empty-mini">플래그된 항목 없음</div>
              )}
            </div>
          </SectionCard>
        </div>

        <div className="admin-row admin-row-1-1">
          <SectionCard
            title="부서별 저장공간"
            subtitle="할당량 대비 사용량"
            right={
              <Link
                href="/admin/storage"
                className="btn-ghost btn-xs"
                style={{ textDecoration: 'none' }}
              >
                관리 →
              </Link>
            }
          >
            <div className="dept-list">
              {ADMIN_DEPARTMENTS.slice(0, 5).map((d) => (
                <DeptRow key={d.id} dept={d} />
              ))}
            </div>
          </SectionCard>
        </div>
      </div>
    </AdminGuard>
  )
}

/**
 * 위젯 영역 운영자 안내 callout — overview 위젯은 backend trend/usage endpoint 미연결
 * mock 상태임을 즉시 인지하도록 한다. 실서비스 KPI 그리드(`<DashboardSummary>`)는
 * 실 데이터이므로 본 callout 의 영향 범위는 위젯 3종에만 한정된다.
 */
function OverviewMockCallout() {
  return (
    <div
      role="note"
      aria-label="overview-mock"
      className="section-card"
      style={{
        background: 'color-mix(in oklch, var(--accent) 5%, var(--surface-1))',
        borderColor: 'color-mix(in oklch, var(--accent) 24%, var(--border))',
      }}
    >
      <div style={{ padding: '12px 16px', fontSize: 12 }}>
        <strong style={{ fontSize: 12.5 }}>v1.x 후속 트랙 — 위젯 mock 데이터</strong>
        <p style={{ margin: '4px 0 0', color: 'var(--fg-muted)' }}>
          아래 업로드 추이 / 플래그된 공유 / 부서별 저장공간 위젯은 디자인 시각 재현
          단계이며, trend·dept-usage backend endpoint 는 v1.x 진입 시 wiring 됩니다.
          상단의 실서비스 KPI 그리드만 실시간 데이터를 반영합니다.
        </p>
      </div>
    </div>
  )
}
