'use client'
import { MoreHorizontal } from 'lucide-react'
import type { AuditLogEntry } from '@/types/audit'
import { severityOf } from '@/lib/admin/auditSeverity'

/**
 * 감사 로그 stream 시각화 — 디자인 핸드오프 2026-05-10 admin.jsx §AdminAudit
 * AuditFullRow (L812~837) + audit-stream (L776~778) 1:1 매핑.
 *
 * <p>각 row 는 5컬럼 grid (audit-time / audit-sev / audit-actor / audit-action /
 * cell-actions). 시간 컬럼은 상대(formatRelative)·절대 시각 두 줄, severity
 * 컬럼은 dot, actor 는 텍스트(Avatar 컴포넌트는 explorer 전용이라 admin
 * 위젯에서 재사용 안 함), action 컬럼은 event type label + resource target +
 * detail.
 *
 * <p>본 컴포넌트는 design fidelity sweep Phase 3d 의 시각 fidelity 재현이며,
 * 데이터는 backend `/api/admin/audit` 의 entries 를 그대로 사용 (mock 아님).
 * severity 컬럼은 frontend severity 매핑(`auditSeverity.severityOf`) — backend
 * severity 컬럼 추가는 v1.x++ 트랙.
 *
 * <p>style: `.audit-stream`, `.audit-row`, `.audit-row.sev-{warn|danger}`,
 * `.audit-time`, `.audit-rel`, `.audit-abs`, `.audit-sev`, `.audit-actor`,
 * `.audit-action`, `.audit-type`, `.audit-target`, `.audit-detail`,
 * `.cell-actions`, `.sev-dot.sev-{info|warn|danger}` (admin.css L582~629).
 * 신규 CSS 없음 — className wiring 만.
 *
 * <p>접근성: 시각적 grid 구조이지만 audit-row 는 의미상 list item — `role="list"`
 * + `role="listitem"` 으로 노출 (table 가 아닌 stream 의 시맨틱).
 */

interface AuditStreamProps {
  entries: AuditLogEntry[]
  isLoading: boolean
  isError: boolean
}

export function AuditStream({ entries, isLoading, isError }: AuditStreamProps) {
  if (isError) {
    return (
      <div role="alert" className="empty-mini" style={{ color: 'var(--danger)' }}>
        감사 로그를 불러오지 못했습니다.
      </div>
    )
  }
  if (isLoading) {
    return <div className="empty-mini">로드 중...</div>
  }
  if (entries.length === 0) {
    return <div className="empty-mini">조건에 맞는 감사 로그가 없습니다.</div>
  }

  return (
    <div
      role="list"
      aria-label="감사 로그 stream"
      className="audit-stream"
      data-testid="audit-stream"
    >
      {entries.map((e) => (
        <AuditStreamRow key={e.id} entry={e} />
      ))}
    </div>
  )
}

function AuditStreamRow({ entry }: { entry: AuditLogEntry }) {
  const sev = severityOf(entry.eventType)
  return (
    <div role="listitem" className={`audit-row sev-${sev}`} data-severity={sev}>
      <div className="audit-time">
        <div className="audit-rel">{formatRelative(entry.occurredAt)}</div>
        <div className="audit-abs">{formatAbs(entry.occurredAt)}</div>
      </div>
      <div className={`audit-sev sev-${sev}`}>
        <span className={`sev-dot sev-${sev}`} aria-hidden="true" />
        <span className="sr-only">{sev}</span>
      </div>
      <div className="audit-actor">
        <span>{entry.actorName}</span>
      </div>
      <div className="audit-action">
        <span className="audit-type">{entry.eventType}</span>
        <span className="audit-target">
          {entry.resourceName ?? (entry.resourceType ? `[${entry.resourceType}]` : '—')}
        </span>
        {entry.ip && <div className="audit-detail">IP: {entry.ip}</div>}
      </div>
      <div className="cell-actions">
        <button type="button" className="icon-btn xs" aria-label="더보기" disabled>
          <MoreHorizontal size={12} />
        </button>
      </div>
    </div>
  )
}

/**
 * 상대 시각 포매터 — "방금", "N분 전", "N시간 전", "M/D" 4단계.
 * design data.js formatRelative 의 frontend 단순화 버전.
 */
function formatRelative(iso: string): string {
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return iso
  const diffMs = Date.now() - t
  if (diffMs < 60_000) return '방금'
  const min = Math.floor(diffMs / 60_000)
  if (min < 60) return `${min}분 전`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}시간 전`
  const d = new Date(t)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** 절대 시각 포매터 — 'YYYY-MM-DD HH:mm' UTC 표시. */
function formatAbs(iso: string): string {
  return iso.replace('T', ' ').replace('Z', '').slice(0, 16)
}
