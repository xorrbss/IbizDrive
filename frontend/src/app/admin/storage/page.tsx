'use client'
import { useAdminStorageOverview } from '@/hooks/useAdminStorageOverview'
import { StorageOverviewCards } from '@/components/admin/StorageOverviewCards'
import { StorageOverviewTable } from '@/components/admin/StorageOverviewTable'
import { SectionCard } from '@/components/admin/SectionCard'
import { DeptRow } from '@/components/admin/overview/DeptRow'
import { CleanupList } from '@/components/admin/storage/CleanupList'
import { AdminGuard } from '@/components/auth/AdminGuard'
import { ADMIN_DEPARTMENTS } from '@/lib/admin/overviewMock'

/**
 * /admin/storage — 시스템 스토리지 합계 + 정리 기록 + 부서별 사용량
 * (admin-storage-overview, docs/04 §스토리지 / design fidelity sweep Phase 3c).
 *
 * <p>구성 (위에서 아래로):
 * <ul>
 *   <li>실서비스 KPI 카드 5장 (`<StorageOverviewCards>`) — backend wiring 완료</li>
 *   <li>orphan-cleanup 마지막 실행 1건 (`<StorageOverviewTable>`) — backend wiring 완료</li>
 *   <li>정리 기록 위젯 (`<CleanupList>`) — mock (cleanup metric endpoint v1.x backlog)</li>
 *   <li>부서별 저장공간 8건 (`<DeptRow>`) — mock (dept-usage endpoint v1.x backlog)</li>
 * </ul>
 *
 * <p>본 페이지는 디자인 핸드오프 2026-05-10 admin.jsx §AdminStorage (L448~517) 의 시각
 * fidelity 재현이다. 실서비스 KPI / orphan 표는 그대로 보존하고, design 의
 * cleanup-list + dept-card 위젯을 추가하여 운영자 화면을 design 일치 단계로 끌어올린다.
 *
 * <p>권한 가드는 backend `@PreAuthorize("hasRole('ADMIN')")` 가 진실, UI 는 UX 용.
 * 401/403 시 retry false 로 즉시 에러 노출.
 *
 * <p>가드: ADMIN-only — layout 이 ADMIN+AUDITOR 허용으로 완화되었으므로 본 페이지는
 * default `<AdminGuard>` 로 다시 좁힌다 (wave1.5-auditor-admin-ui-access).
 */
export default function AdminStoragePage() {
  return (
    <AdminGuard>
      <StoragePageBody />
    </AdminGuard>
  )
}

function StoragePageBody() {
  const { data, isLoading, isError } = useAdminStorageOverview()

  return (
    <div className="admin-grid">
      <header>
        <h1 className="text-[20px] font-semibold text-fg mb-1">스토리지</h1>
        <p className="text-[13px] text-fg-2">
          시스템 전체 파일/버전/휴지통 합계와 정리 기록·부서별 사용량을 보여줍니다.
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

      <StorageMockCallout />

      <div className="admin-row admin-row-1-1">
        <SectionCard title="정리 기록" subtitle="최근 작업 · 4 카테고리">
          <CleanupList />
        </SectionCard>

        <SectionCard
          title="부서별 저장공간"
          subtitle="할당량 대비 사용량"
          right={<span className="admin-mini-stat">{ADMIN_DEPARTMENTS.length}개 부서</span>}
        >
          <div className="dept-list">
            {ADMIN_DEPARTMENTS.map((d) => (
              <DeptRow key={d.id} dept={d} />
            ))}
          </div>
        </SectionCard>
      </div>
    </div>
  )
}

/**
 * 위젯 영역 운영자 안내 callout — 정리 기록 / 부서별 사용량 위젯은 backend cleanup-history
 * / dept-usage endpoint 미연결 mock 상태임을 즉시 인지하도록 한다. 상단의 KPI 카드 +
 * orphan 표는 실 데이터이므로 본 callout 의 영향 범위는 위젯 2종에만 한정된다.
 */
function StorageMockCallout() {
  return (
    <div
      role="note"
      aria-label="storage-mock"
      className="section-card"
      style={{
        background: 'color-mix(in oklch, var(--accent) 5%, var(--surface-1))',
        borderColor: 'color-mix(in oklch, var(--accent) 24%, var(--border))',
      }}
    >
      <div style={{ padding: '12px 16px', fontSize: 12 }}>
        <strong style={{ fontSize: 12.5 }}>v1.x 후속 트랙 — 위젯 mock 데이터</strong>
        <p style={{ margin: '4px 0 0', color: 'var(--fg-muted)' }}>
          아래 정리 기록 / 부서별 저장공간 위젯은 디자인 시각 재현 단계이며,
          cleanup-history·dept-usage backend endpoint 는 v1.x 진입 시 wiring 됩니다.
          상단의 KPI 카드 + orphan 마지막 실행 표만 실시간 데이터를 반영합니다.
        </p>
      </div>
    </div>
  )
}
