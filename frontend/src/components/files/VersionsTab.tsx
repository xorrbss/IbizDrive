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
    <ul className="space-y-2" aria-label="파일 버전 목록">
      {data.map((v) => (
        <VersionRow
          key={v.id}
          version={v}
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
  onDownload,
  onRestore,
  isPending,
}: {
  version: FileVersionDto
  onDownload: () => void
  onRestore: () => void
  isPending: boolean
}) {
  return (
    <li className="border border-border rounded px-2.5 py-2 bg-surface-1">
      <div className="flex items-center gap-2">
        <span className="text-[12px] font-semibold text-fg tabular-nums">
          v{version.versionNumber}
        </span>
        {version.isCurrent && (
          <span
            className="text-[10px] px-1.5 py-0.5 rounded bg-accent/20 text-accent"
            aria-label="현재 버전"
          >
            현재
          </span>
        )}
        <span className="ml-auto text-[11px] text-fg-muted tabular-nums">
          {formatSize(version.sizeBytes)}
        </span>
      </div>
      <div className="mt-1 flex items-center gap-2 text-[11px] text-fg-muted">
        <span className="tabular-nums">{formatDate(version.uploadedAt)}</span>
        <span>·</span>
        <span className="truncate">{version.uploadedBy}</span>
      </div>
      {version.comment && (
        <div className="mt-1 text-[11px] text-fg-muted truncate" title={version.comment}>
          {version.comment}
        </div>
      )}
      <div className="mt-2 flex items-center gap-1.5">
        <button
          type="button"
          onClick={onDownload}
          disabled={isPending}
          className="text-[11px] px-2 py-1 rounded border border-border bg-surface-2 hover:bg-surface-3 disabled:opacity-50 disabled:cursor-not-allowed"
          aria-label={`v${version.versionNumber} 다운로드`}
        >
          다운로드
        </button>
        <button
          type="button"
          onClick={onRestore}
          disabled={version.isCurrent || isPending}
          className="text-[11px] px-2 py-1 rounded border border-border bg-surface-2 hover:bg-surface-3 disabled:opacity-50 disabled:cursor-not-allowed"
          aria-label={`v${version.versionNumber} 복원`}
        >
          복원
        </button>
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
