'use client'
import Link from 'next/link'
import { useAdminTrashPolicy } from '@/hooks/useAdminTrashPolicy'
import { AdminGuard } from '@/components/auth/AdminGuard'
import { RetentionPolicyEditor } from '@/components/admin/RetentionPolicyEditor'
import { SectionCard } from '@/components/admin/SectionCard'
import { LegalHoldList } from '@/components/admin/retention/LegalHoldList'

/**
 * /admin/retention — 휴지통 보존 정책 viewer + mutation editor
 * (wave2-trash-policy-viewer + trash-retention-mutation Phase C).
 *
 * <p>현재 보존 일수(V17 `trash_policy.retention_days`) 카드 + mutation editor + cron cross-link.
 * mutation은 단일-approver MVP — 단일 ADMIN 즉시 적용. 변경은 신규 soft-delete만 적용
 * (기존 trash row의 `purge_after`는 재계산 안 함, 일수 감소 시 hard purge 폭증 회피).
 *
 * <p>cron 운영 상태(enabled/cron/zone)는 `/admin/system`이 진실의 출처라 본 페이지는
 * cross-link만 제공하고 직접 노출하지 않는다 (admin-cron-toggle 트랙과 분리).
 *
 * <p>가드: ADMIN-only — default `<AdminGuard>`로 좁힌다
 * (wave1.5-auditor-admin-ui-access 패턴).
 */
export default function AdminTrashPolicyPage() {
  return (
    <AdminGuard>
      <PolicyPageBody />
    </AdminGuard>
  )
}

function PolicyPageBody() {
  const { data, isLoading, isError } = useAdminTrashPolicy()

  return (
    <div className="flex-1 overflow-auto p-6 space-y-6">
      <header>
        <h1 className="text-lg font-semibold">휴지통 보존 정책</h1>
        <p className="text-[12px] text-fg-2 mt-1">
          현재 적용 중인 휴지통 보존 일수를 확인하고 변경할 수 있습니다. 변경은
          단일 ADMIN 즉시 적용되며 신규 삭제부터 새 일수가 적용됩니다 (기존 휴지통
          항목의 영구 삭제일은 변경되지 않음).
        </p>
      </header>

      {isLoading && <p className="text-sm text-fg-2">불러오는 중…</p>}

      {isError && (
        <p role="alert" className="text-sm text-red-600">
          휴지통 보존 정책을 불러오지 못했습니다.
        </p>
      )}

      {data && (
        <>
          <section
            aria-labelledby="retention-heading"
            className="rounded border border-border p-4 space-y-2 max-w-md"
          >
            <h2 id="retention-heading" className="text-sm font-medium text-fg-2">
              현재 보존 기간
            </h2>
            <p className="tabular-nums text-2xl font-semibold">
              {data.retentionDays}
              <span className="text-base font-normal text-fg-2 ml-1">일</span>
            </p>
            <p className="text-[12px] text-fg-2">
              soft-delete 시점 기준. 이 기간이 지난 항목은 hard purge cron의 삭제 후보가 됩니다.
            </p>
          </section>

          <RetentionPolicyEditor currentDays={data.retentionDays} />
        </>
      )}

      <section
        aria-labelledby="cron-heading"
        className="rounded border border-border p-4 space-y-2 max-w-md"
      >
        <h2 id="cron-heading" className="text-sm font-medium text-fg-2">
          hard purge cron 운영 상태
        </h2>
        <p className="text-[12px] text-fg-2">
          cron 활성/비활성과 스케줄은 별도 페이지에서 관리합니다.
        </p>
        <Link
          href="/admin/system"
          className="text-sm underline text-blue-600 hover:text-blue-700"
        >
          /admin/system 으로 이동 →
        </Link>
      </section>

      <section
        aria-labelledby="approval-heading"
        className="rounded border border-border p-4 space-y-2 max-w-md"
      >
        <h2 id="approval-heading" className="text-sm font-medium text-fg-2">
          2인 승인
        </h2>
        <p className="text-[12px] text-fg-2">
          현재는 단일 ADMIN 즉시 적용. v1.x++에서 <strong>2인 승인(dual-approval)</strong>{' '}
          workflow가 도입되면 본 endpoint(<code className="bg-surface-2 px-1 rounded">PUT /api/admin/trash/policy</code>)
          가 hook point가 됩니다 (운영 런북 docs/04 §16 / ADR #47).
        </p>
      </section>

      <LegalHoldMockCallout />

      <SectionCard title="법적 보존 (Legal Hold)" subtitle="삭제 보호 중인 항목 — mock 위젯">
        <LegalHoldList />
      </SectionCard>
    </div>
  )
}

/**
 * Legal Hold 위젯 운영자 안내 callout — Legal Hold 본 기능은 `docs/03 §6.3` 에
 * v2.x deferred. 본 위젯은 design 시각 fidelity 재현이며 backend endpoint
 * 합류 전이라 mock 데이터를 표시함을 즉시 인지하도록 한다 (상단 보존 정책 +
 * mutation editor 는 실 데이터이므로 callout 영향 범위는 Legal Hold 위젯에만
 * 한정된다).
 */
function LegalHoldMockCallout() {
  return (
    <div
      role="note"
      aria-label="legal-hold-mock"
      className="section-card"
      style={{
        background: 'color-mix(in oklch, var(--accent) 5%, var(--surface-1))',
        borderColor: 'color-mix(in oklch, var(--accent) 24%, var(--border))',
        maxWidth: 720,
      }}
    >
      <div style={{ padding: '12px 16px', fontSize: 12 }}>
        <strong style={{ fontSize: 12.5 }}>v2.x 후속 트랙 — Legal Hold mock</strong>
        <p style={{ margin: '4px 0 0', color: 'var(--fg-muted)' }}>
          아래 법적 보존 위젯은 디자인 시각 재현 단계이며, Legal Hold 본 기능은
          docs/03 §6.3 에 v2.x deferred 로 명시되어 있습니다. backend endpoint
          (GET/PUT /api/admin/legal-holds) 가 합류하면 hook 으로 교체됩니다.
        </p>
      </div>
    </div>
  )
}
