'use client'
import { useFileActivity } from '@/hooks/useFileActivity'
import type { AuditEventType, AuditLogEntry } from '@/types/audit'

/**
 * RightPanel `activity` 탭 본문 (M-RP.4 / ADR #40 RP-2).
 *
 * - 호출자(RightPanel)가 `tab === 'activity'`일 때만 mount → 비활성 탭 fetch 차단.
 * - read-only: backend 정책 분기는 service 단일 진입점에서. 프론트는 결과만 표시.
 * - MVP 페이지 1만 (pageSize 20). 더보기/페이지네이션은 v1.x 백로그.
 * - 빈 상태/에러/로딩 모두 작은 메시지 — VersionsTab 패턴 일관 (KISS).
 *
 * 권한 분기 결과 노출 차이:
 * - ADMIN/AUDITOR: 모든 actor 이벤트
 * - MEMBER + READ on file: 모든 actor 이벤트 (RP-2)
 * - MEMBER without READ: 자기 이벤트만 (또는 빈 페이지)
 * 프론트는 각 케이스를 구분 표시하지 않는다 — backend가 보안 boundary, 프론트는 응답만 렌더.
 */
export function ActivityTab({ fileId }: { fileId: string }) {
  const { data, isLoading, error } = useFileActivity(fileId)

  if (isLoading) {
    return (
      <div className="text-fg-muted text-[12px] py-4 text-center" aria-live="polite">
        활동을 불러오는 중…
      </div>
    )
  }
  if (error) {
    return (
      <div role="alert" className="text-[12px] text-danger">
        활동을 불러오지 못했습니다.
      </div>
    )
  }
  if (!data || data.entries.length === 0) {
    return (
      <div className="text-fg-muted text-[12px] py-4 text-center">
        활동 내역이 없습니다.
      </div>
    )
  }

  return (
    <ol aria-label="파일 활동 타임라인" className="flex flex-col gap-2">
      {data.entries.map((e) => (
        <ActivityRow key={e.id} entry={e} />
      ))}
    </ol>
  )
}

/** Audit eventType → 한글 라벨. backend enum과 분리된 표시용 (i18n 후속 트랙). */
const EVENT_LABELS: Partial<Record<AuditEventType, string>> = {
  'file.uploaded': '업로드',
  'file.viewed': '조회',
  'file.downloaded': '다운로드',
  'file.renamed': '이름 변경',
  'file.moved': '이동',
  'file.deleted': '삭제',
  'file.restored': '복원',
  'file.purged': '영구 삭제',
  'version.created': '새 버전',
  'version.restored': '버전 복원',
  'version.downloaded': '버전 다운로드',
  'permission.granted': '권한 부여',
  'permission.revoked': '권한 회수',
  'permission.changed': '권한 변경',
  'permission.expired': '권한 만료',
  'share.created': '공유 생성',
  'share.revoked': '공유 해제',
  'share.expired': '공유 만료',
}

function ActivityRow({ entry }: { entry: AuditLogEntry }) {
  const label = EVENT_LABELS[entry.eventType] ?? entry.eventType
  return (
    <li
      data-event-type={entry.eventType}
      className="border border-border rounded px-2.5 py-2 bg-surface-1"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-[12px] font-medium text-fg">{label}</span>
        <time
          dateTime={entry.occurredAt}
          className="text-[11px] text-fg-muted tabular-nums"
        >
          {formatTime(entry.occurredAt)}
        </time>
      </div>
      <div className="text-[11px] text-fg-muted mt-0.5 truncate">
        {entry.actorName}
      </div>
    </li>
  )
}

/** ISO 8601 → 로컬 표시 (YYYY-MM-DD HH:mm). 잘못된 ISO는 원본 echo. */
function formatTime(iso: string): string {
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
