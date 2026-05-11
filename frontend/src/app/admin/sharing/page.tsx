import { AdminGuard } from '@/components/auth/AdminGuard'
import { SectionCard } from '@/components/admin/SectionCard'
import { SharingPolicies } from '@/components/admin/sharing/SharingPolicies'
import { SharingDomains } from '@/components/admin/sharing/SharingDomains'
import { SharingFlagged } from '@/components/admin/sharing/SharingFlagged'
import { ADMIN_FLAGGED } from '@/lib/admin/sharingMock'

/**
 * `/admin/sharing` — 공유 정책 페이지 (design fidelity sweep Phase 3a).
 *
 * <p>디자인 핸드오프 2026-05-10 admin.jsx §AdminSharing (L582~706) 1:1 매핑:
 * <ul>
 *   <li>검토 대기 공유 큐 (`<SharingFlagged>`) — flagged share 검토 UI</li>
 *   <li>외부 공유 정책 (`<SharingPolicies>`) — 도메인/만료/공개링크/다운로드 4개 policy row</li>
 *   <li>도메인 정책 (`<SharingDomains>`) — allow/block 도메인 + SSO/MFA</li>
 * </ul>
 *
 * <p>본 페이지는 frontend visual fidelity sweep 단계 — 실제 sharing-policy
 * backend endpoint(POST/PUT/DELETE)는 v1.x backlog에 유지된다 (`docs/v1x-backlog.md`).
 * 모든 mutation 버튼은 disabled + tooltip("v1.x 후속 트랙") 처리, 도메인 추가/제거는
 * `useState`로 frontend-only 동작.
 *
 * <p>가드: ADMIN-only — layout이 ADMIN+AUDITOR 허용으로 완화되었으므로 본 페이지는
 * default `<AdminGuard>`로 다시 좁힌다 (wave1.5-auditor-admin-ui-access).
 * mutation endpoint 합류 시 백엔드 `@PreAuthorize`로 재검증한다 (CLAUDE.md §3 #10).
 */
export default function AdminSharingPage() {
  return (
    <AdminGuard>
      <div className="admin-grid">
        <BacklogCallout />

        <SectionCard
          title="검토 대기 공유"
          subtitle={`${ADMIN_FLAGGED.length}건 — 자동 정책 위반 또는 PII 가능성`}
        >
          <SharingFlagged />
        </SectionCard>

        <div className="admin-row admin-row-1-1">
          <SectionCard title="외부 공유 정책" subtitle="기본 동작">
            <SharingPolicies />
          </SectionCard>

          <SectionCard title="도메인 정책" subtitle="허용 / 차단 도메인">
            <SharingDomains />
          </SectionCard>
        </div>
      </div>
    </AdminGuard>
  )
}

/**
 * 페이지 상단 callout — backend endpoint가 v1.x backlog임을 명시.
 * 운영자가 mutation 시도 시 실제 반영되지 않는다는 점을 페이지 진입 즉시 인지하도록 한다.
 */
function BacklogCallout() {
  return (
    <div
      role="note"
      aria-label="v1.x backlog"
      className="section-card"
      style={{
        background: 'color-mix(in oklch, var(--accent) 5%, var(--surface-1))',
        borderColor: 'color-mix(in oklch, var(--accent) 24%, var(--border))',
      }}
    >
      <div style={{ padding: '12px 16px', fontSize: 12 }}>
        <strong style={{ fontSize: 12.5 }}>v1.x 후속 트랙</strong>
        <p style={{ margin: '4px 0 0', color: 'var(--fg-muted)' }}>
          본 페이지는 디자인 핸드오프(admin.jsx §AdminSharing) 시각 재현 단계입니다.
          공유 정책 backend endpoint(외부 도메인 정책 / 플래그 검토 / 도메인 allow·block
          mutation)는 v1.x backlog 상태로, 도메인 추가·제거는 화면 내에서만 반영되며
          새로고침 시 mock 기본값으로 돌아갑니다.
        </p>
      </div>
    </div>
  )
}

