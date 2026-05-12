import type { AuditLogEntry } from '@/types/audit'

/**
 * Overview 페이지용 audit mini row — 디자인 핸드오프 2026-05-10 admin.jsx
 * §AuditMiniRow (L794~810) 1:1 매핑.
 *
 * <p>4-col grid (severity dot / avatar slot / main text / time). 본 컴포넌트는
 * Avatar 컴포넌트를 사용하지 않고 actor 이니셜 텍스트 슬롯으로 대체 —
 * admin 위젯은 explorer 의 Avatar 컴포넌트와 의존성 분리(KISS).
 *
 * <p>data source: 실 backend `useAuditLogs` 의 `entries` 를 그대로 슬라이스해
 * 사용한다. mock 아님. severity 는 V19 backend `audit_log.severity` 단일 진실 —
 * `entry.severity` 를 그대로 사용한다.
 *
 * <p>style: `.audit-mini`, `.audit-mini-line`, `.audit-mini-target`,
 * `.audit-mini-time`, `.sev-dot.sev-{info|warn|danger}` (admin.css L533~547).
 */
export interface AuditMiniRowProps {
  entry: AuditLogEntry
}

export function AuditMiniRow({ entry }: AuditMiniRowProps) {
  const sev = entry.severity
  const target = entry.resourceName ?? (entry.resourceType ? `[${entry.resourceType}]` : '—')
  return (
    <div className="audit-mini">
      <span className={`sev-dot sev-${sev}`} aria-hidden="true" />
      <span aria-hidden="true" />
      <div className="audit-mini-line">
        <strong>{entry.actorName}</strong>
        <span className="muted"> {auditVerb(entry.eventType)} </span>
        <span className="audit-mini-target">{target}</span>
      </div>
      <span className="audit-mini-time">{formatRelative(entry.occurredAt)}</span>
    </div>
  )
}

/**
 * Event type → 자연어 verb. design data.js `auditVerb` 의 frontend 단순화. 명시
 * 매핑이 없으면 event type 그대로 노출.
 */
function auditVerb(type: AuditLogEntry['eventType']): string {
  switch (type) {
    case 'file.uploaded': return '업로드'
    case 'file.downloaded': return '다운로드'
    case 'file.deleted': return '삭제'
    case 'file.restored': return '복원'
    case 'file.renamed': return '이름 변경'
    case 'file.moved': return '이동'
    case 'folder.created': return '폴더 생성'
    case 'folder.renamed': return '폴더 이름 변경'
    case 'folder.deleted': return '폴더 삭제'
    case 'permission.granted': return '권한 부여'
    case 'permission.revoked': return '권한 회수'
    case 'permission.changed': return '권한 변경'
    case 'share.created': return '공유 생성'
    case 'share.revoked': return '공유 해제'
    case 'version.created': return '버전 추가'
    case 'user.login.success': return '로그인'
    case 'user.login.failed': return '로그인 실패'
    case 'admin.user.created': return '사용자 생성'
    case 'admin.user.deactivated': return '사용자 비활성화'
    case 'admin.role.changed': return '역할 변경'
    case 'admin.legal_hold.placed': return 'Legal Hold 적용'
    case 'admin.legal_hold.released': return 'Legal Hold 해제'
    default: return type
  }
}

/** 상대 시간 — FlagRow / AuditStream 의 helper 와 동일 의도, KISS 로 inline. */
function formatRelative(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return iso
  const diffMs = Date.now() - then
  if (diffMs < 60_000) return '방금'
  const min = Math.floor(diffMs / 60_000)
  if (min < 60) return `${min}분 전`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}시간 전`
  const d = new Date(then)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
