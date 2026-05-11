'use client'
import { useFileVersions } from '@/hooks/useFileVersions'
import { useRestoreVersion } from '@/hooks/useRestoreVersion'
import { api } from '@/lib/api'
import type { FileVersionDto } from '@/types/version'

/**
 * RightPanel `versions` 탭 본문 (M-RP.1 + M-RP.2.3).
 *
 * - 호출자(RightPanel)가 `tab === 'versions'`로 conditional render — 비활성 탭에서는 mount되지 않아
 *   자동으로 fetch 차단. 별도 enabled 가드 불필요.
 * - 상태 4분기: 로딩 / 에러 / 빈 / 리스트 — RightPanel detail 탭의 PanelSkeleton/PanelError 톤 답습.
 * - 정렬: backend가 versionNumber DESC로 보장 (FileVersionRepository 메소드 시그니처).
 * - 행 액션: 다운로드(모든 row, READ 권한이면 backend가 허용) + 복원(비-current에 한해 활성, EDIT 권한
 *   필요). 복원 mid-flight는 두 버튼 모두 비활성으로 잠가 race 회피 (KISS — 단일 isPending 사용).
 * - 시각: zip `panels.jsx` + `styles.css` (L893~935) 타임라인 패턴
 *   (`grid-template-columns: 16px 1fr` 좌측 dot+line gutter + 우측 body).
 *   기능 invariant 유지(audit/멱등 영향 zero).
 */
export function VersionsTab({ fileId }: { fileId: string }) {
  const { data, isLoading, isError } = useFileVersions(fileId)
  const restore = useRestoreVersion()

  if (isLoading) return <Skeleton />
  if (isError) {
    return (
      <div role="alert" className="text-[12px] text-danger">
        버전 목록을 불러오지 못했습니다.
      </div>
    )
  }
  if (!data || data.length === 0) {
    return (
      <div className="text-fg-muted text-[12px] py-4 text-center">
        버전이 없습니다.
      </div>
    )
  }

  return (
    <ul className="flex flex-col" aria-label="파일 버전 목록">
      {data.map((v, idx) => (
        <VersionRow
          key={v.id}
          version={v}
          isLast={idx === data.length - 1}
          onDownload={() => api.downloadVersion(fileId, v.id)}
          onRestore={() => restore.mutate({ fileId, versionId: v.id })}
          isPending={restore.isPending}
        />
      ))}
    </ul>
  )
}

function Skeleton() {
  return (
    <div className="space-y-2 animate-pulse" aria-hidden>
      <div className="h-8 bg-surface-2 rounded" />
      <div className="h-8 bg-surface-2 rounded" />
      <div className="h-8 bg-surface-2 rounded" />
    </div>
  )
}

function VersionRow({
  version,
  isLast,
  onDownload,
  onRestore,
  isPending,
}: {
  version: FileVersionDto
  isLast: boolean
  onDownload: () => void
  onRestore: () => void
  isPending: boolean
}) {
  // zip styles.css L894~921: grid 16px gutter (dot 8px + 1px vertical line)
  // dot 색: current 면 --accent, 아니면 --surface-3. line 색: --border, 마지막 row hide.
  return (
    <li className="grid grid-cols-[16px_1fr] gap-2.5 py-2 relative">
      {/* gutter: dot + 세로 라인 */}
      <div className="flex flex-col items-center relative">
        <div
          className={
            'w-2 h-2 rounded-full mt-1.5 z-[1] ' +
            (version.isCurrent
              ? 'bg-accent shadow-[0_0_0_1px_var(--accent)]'
              : 'bg-surface-3')
          }
          aria-hidden
        />
        {!isLast && (
          <div
            className="absolute top-3.5 -bottom-2 w-px bg-border"
            aria-hidden
          />
        )}
      </div>

      {/* body */}
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <span className="text-[11.5px] font-semibold text-fg tabular-nums font-mono">
            v{version.versionNumber}
          </span>
          {version.isCurrent && (
            <span
              className="text-[10px] px-1.5 leading-[1.4] rounded bg-accent text-accent-fg font-semibold"
              aria-label="현재 버전"
            >
              현재
            </span>
          )}
          <span className="ml-auto text-[11px] text-fg-muted tabular-nums">
            {formatSize(version.sizeBytes)}
          </span>
        </div>
        {version.comment ? (
          <div className="text-[12px] text-fg-2 truncate" title={version.comment}>
            {version.comment}
          </div>
        ) : (
          <div className="text-[12px] text-fg-subtle">메모 없음</div>
        )}
        <div className="flex items-center gap-1.5 text-[11px] text-fg-muted">
          <span className="truncate">{version.uploadedBy}</span>
          <span className="text-fg-subtle">·</span>
          <span className="tabular-nums">{formatDate(version.uploadedAt)}</span>
        </div>
        <div className="flex items-center gap-1 mt-0.5">
          <button
            type="button"
            onClick={onDownload}
            disabled={isPending}
            className="text-[11px] px-2 py-1 rounded text-fg-2 hover:bg-surface-2 disabled:opacity-50 disabled:cursor-not-allowed"
            aria-label={`v${version.versionNumber} 다운로드`}
          >
            다운로드
          </button>
          <button
            type="button"
            onClick={onRestore}
            disabled={version.isCurrent || isPending}
            className="text-[11px] px-2 py-1 rounded text-fg-2 hover:bg-surface-2 disabled:opacity-50 disabled:cursor-not-allowed"
            aria-label={`v${version.versionNumber} 복원`}
          >
            이 버전으로 복원
          </button>
        </div>
      </div>
    </li>
  )
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
