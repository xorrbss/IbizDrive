'use client'
import Link from 'next/link'
import { useAdminTrashPolicy } from '@/hooks/useAdminTrashPolicy'
import { AdminGuard } from '@/components/auth/AdminGuard'

/**
 * /admin/trash/policy — 휴지통 보존 정책 read-only viewer
 * (wave2-trash-policy-viewer, Wave 2 T9 follow-up).
 *
 * <p>현재 보존 일수(`app.trash.retention.days`, default 30) 단일 카드 + 변경 절차 안내.
 * mutation은 v1.x deferred — 운영자는 `application.yml` 수정 + 재기동으로 변경.
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
          현재 적용 중인 휴지통 보존 일수와 변경 절차를 안내합니다. 변경은
          관리자 yml 수정 + 재기동이 필요합니다 (무중단 변경은 v1.x 예정).
        </p>
      </header>

      {isLoading && <p className="text-sm text-fg-2">불러오는 중…</p>}

      {isError && (
        <p role="alert" className="text-sm text-red-600">
          휴지통 보존 정책을 불러오지 못했습니다.
        </p>
      )}

      {data && (
        <section
          aria-labelledby="retention-heading"
          className="rounded border border-border p-4 space-y-2 max-w-md"
        >
          <h2 id="retention-heading" className="text-sm font-medium text-fg-2">
            보존 기간
          </h2>
          <p className="tabular-nums text-2xl font-semibold">
            {data.retentionDays}
            <span className="text-base font-normal text-fg-2 ml-1">일</span>
          </p>
          <p className="text-[12px] text-fg-2">
            soft-delete 시점 기준. 이 기간이 지난 항목은 hard purge cron의 삭제 후보가 됩니다.
          </p>
        </section>
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
        aria-labelledby="change-heading"
        className="rounded border border-border p-4 space-y-2 max-w-md"
      >
        <h2 id="change-heading" className="text-sm font-medium text-fg-2">
          보존 일수 변경 방법
        </h2>
        <ol className="list-decimal pl-5 space-y-1 text-[13px] text-fg-2">
          <li>
            <code className="bg-bg-2 px-1 rounded">application.yml</code> 의{' '}
            <code className="bg-bg-2 px-1 rounded">app.trash.retention.days</code> 값 변경
          </li>
          <li>backend 재기동 (1회) — Spring `@ConfigurationProperties` 부팅 시 바인딩</li>
          <li>본 페이지로 돌아와 변경된 값 확인</li>
        </ol>
        <p className="text-[12px] text-fg-2">
          0/음수 입력은 default 30으로 보정됩니다 (즉시 hard purge 사고 방지).
        </p>
      </section>
    </div>
  )
}
