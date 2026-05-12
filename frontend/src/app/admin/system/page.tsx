'use client'
import { useState } from 'react'
import { useAdminSystemCron, useAdminToggleCron } from '@/hooks/useAdminSystem'
import { useMe } from '@/hooks/useMe'
import type { CronJobStatus } from '@/types/system'

/**
 * `/admin/system` (Wave 1 — T3, admin-cron-policy-toggle 확장).
 *
 * <p>4 카드 viewer + ADMIN-only 토글 switch + ConfirmDialog. AUDITOR는 viewer 그대로
 * (토글 미노출). 프론트 권한 가드는 UX용이며, 실제 보안은 backend
 * `@PreAuthorize("hasRole('ADMIN')")` (docs/03 §3 + 핵심 원칙 #10).
 *
 * <p>토글 mutation은 `useAdminToggleCron`(invalidateQueries로 viewer 즉시 refetch).
 * 비활성화/활성화 양방향 모두 ConfirmDialog 거침 — 실수 방지 + 감사 로그(P2) 의도성 확보.
 */
export default function AdminSystemPage() {
  const { data, isLoading, isError } = useAdminSystemCron()
  const { data: me } = useMe()
  const isAdmin = me?.roles?.includes('ADMIN') ?? false

  return (
    <div className="admin-grid">
      <header>
        <h1 className="text-[20px] font-semibold text-fg mb-1">시스템 정책</h1>
        <p className="text-[13px] text-fg-2">
          운영 cron 잡 현재 설정. ADMIN은 enabled를 토글할 수 있습니다(즉시 반영, 다음 tick부터).
        </p>
      </header>
      {isLoading && (
        <div className="rounded border border-border p-4 text-[13px] text-fg-2">
          불러오는 중…
        </div>
      )}
      {isError && (
        <div className="rounded border border-border p-4 text-[13px] text-fg-2" role="alert">
          시스템 설정을 불러오지 못했습니다.
        </div>
      )}
      {data && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {data.jobs.map((job) => (
            <CronCard key={job.key} job={job} canToggle={isAdmin} />
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * 단일 cron 잡 카드. enabled 배지 색상은 의미 강조(ON=accent, OFF=muted)지만 색만으로 의미를
 * 전달하지 않도록 텍스트 라벨(ON/OFF)을 항상 함께 노출한다 (docs/01 §12 접근성).
 *
 * <p>{@code canToggle=true} (ADMIN)일 때만 토글 버튼을 노출한다. 클릭 시 inline ConfirmDialog
 * 가 열리고 확인하면 mutation 실행. mutation pending 동안 토글 버튼은 disabled.
 */
function CronCard({ job, canToggle }: { job: CronJobStatus; canToggle: boolean }) {
  const [pending, setPending] = useState<boolean | null>(null)
  const toggle = useAdminToggleCron()
  const badgeClass = job.enabled
    ? 'bg-accent text-accent-fg'
    : 'bg-surface-2 text-fg-muted'

  return (
    <div
      data-testid={`cron-card-${job.key}`}
      className="rounded border border-border bg-surface-1 p-4 flex flex-col gap-2"
    >
      <div className="flex items-center justify-between gap-2">
        <div className="text-[14px] font-medium text-fg">{job.label}</div>
        <div className="flex items-center gap-2">
          <span
            className={`text-[11px] font-semibold px-1.5 py-0.5 rounded ${badgeClass}`}
            aria-label={job.enabled ? '활성' : '비활성'}
          >
            {job.enabled ? 'ON' : 'OFF'}
          </span>
          {canToggle && (
            <button
              type="button"
              data-testid={`cron-toggle-${job.key}`}
              aria-label={`${job.label} 토글`}
              className="text-[11px] px-2 py-0.5 rounded border border-border hover:bg-bg-subtle disabled:opacity-50"
              disabled={toggle.isPending}
              onClick={() => setPending(!job.enabled)}
            >
              {job.enabled ? '비활성화' : '활성화'}
            </button>
          )}
        </div>
      </div>
      <div className="text-[11px] text-fg-muted font-mono">{job.key}</div>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-[12px]">
        <dt className="text-fg-muted">cron</dt>
        <dd className="font-mono text-fg-2">{job.cron}</dd>
        <dt className="text-fg-muted">zone</dt>
        <dd className="text-fg-2">{job.zone}</dd>
        {job.batchSize !== undefined && (
          <>
            <dt className="text-fg-muted">batchSize</dt>
            <dd className="text-fg-2">{job.batchSize}</dd>
          </>
        )}
        {job.maxPerRun !== undefined && (
          <>
            <dt className="text-fg-muted">maxPerRun</dt>
            <dd className="text-fg-2">{job.maxPerRun}</dd>
          </>
        )}
        {job.graceHours !== undefined && (
          <>
            <dt className="text-fg-muted">graceHours</dt>
            <dd className="text-fg-2">{job.graceHours}</dd>
          </>
        )}
      </dl>

      {pending !== null && (
        <ConfirmDialog
          job={job}
          requested={pending}
          onCancel={() => setPending(null)}
          onConfirm={() => {
            toggle.mutate(
              { key: job.key, enabled: pending },
              { onSettled: () => setPending(null) },
            )
          }}
        />
      )}
    </div>
  )
}

/**
 * Inline ConfirmDialog — 재사용 가능한 공통 컴포넌트 미존재 (admin/trash/all 패턴 미러,
 * KISS § 도메인 외 공유 추상화 보류). 활성/비활성 방향에 따라 title/body가 분기된다.
 */
function ConfirmDialog({
  job,
  requested,
  onCancel,
  onConfirm,
}: {
  job: CronJobStatus
  requested: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  const title = requested ? '정책 활성화' : '정책 비활성화'
  const body = requested
    ? `'${job.label}' cron을 활성화합니다. 다음 실행부터 schedule(${job.cron})에 따라 동작합니다. 계속하시겠습니까?`
    : `'${job.label}' cron을 비활성화합니다. 다음 실행부터 skip되며 진행 중인 작업은 정상 완료됩니다. 계속하시겠습니까?`
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="cron-confirm-title"
      data-testid="cron-confirm-dialog"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-surface-1 border border-border rounded-md shadow-2xl p-5 max-w-[420px] w-full">
        <h2 id="cron-confirm-title" className="text-[15px] font-semibold text-fg mb-2">
          {title}
        </h2>
        <p className="text-[13px] text-fg-2 mb-4">{body}</p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            data-testid="cron-confirm-cancel"
            onClick={onCancel}
            className="text-[12.5px] px-3 py-1.5 rounded text-fg-2 hover:bg-surface-2"
          >
            취소
          </button>
          <button
            type="button"
            data-testid="cron-confirm-confirm"
            onClick={onConfirm}
            className="text-[12.5px] px-3 py-1.5 rounded bg-accent text-accent-fg font-medium hover:opacity-90"
          >
            확인
          </button>
        </div>
      </div>
    </div>
  )
}
