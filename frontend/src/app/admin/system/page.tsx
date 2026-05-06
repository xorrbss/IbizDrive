'use client'
import { useAdminSystemCron } from '@/hooks/useAdminSystem'
import type { CronJobStatus } from '@/types/system'

/**
 * `/admin/system` (Wave 1 — T3) — 운영 cron 4종 설정 read-only 노출.
 *
 * <p>4 카드가 fixed order(backend 응답 순서)로 렌더된다. 카드 본문은 enabled 배지(ON/OFF) +
 * cron 표현식 + zone + 잡-specific 파라미터(batchSize/maxPerRun/graceHours). 변경 UI는
 * v1.x deferred — 페이지 헤더에 "변경은 application.yml + 재기동" 안내.
 */
export default function AdminSystemPage() {
  const { data, isLoading, isError } = useAdminSystemCron()

  return (
    <div className="p-8 max-w-[960px]">
      <h1 className="text-[20px] font-semibold text-fg mb-1">시스템 정책</h1>
      <p className="text-[13px] text-fg-2 mb-6">
        운영 cron 잡 현재 설정 (read-only). 변경은 application.yml 수정 + 재기동이 필요합니다.
      </p>
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
            <CronCard key={job.key} job={job} />
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * 단일 cron 잡 카드. enabled 배지 색상은 의미 강조(ON=accent, OFF=muted)지만 색만으로 의미를
 * 전달하지 않도록 텍스트 라벨(ON/OFF)을 항상 함께 노출한다 (docs/01 §12 접근성).
 */
function CronCard({ job }: { job: CronJobStatus }) {
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
        <span
          className={`text-[11px] font-semibold px-1.5 py-0.5 rounded ${badgeClass}`}
          aria-label={job.enabled ? '활성' : '비활성'}
        >
          {job.enabled ? 'ON' : 'OFF'}
        </span>
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
    </div>
  )
}
