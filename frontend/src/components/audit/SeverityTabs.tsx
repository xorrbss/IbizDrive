'use client'
import type { AuditLogEntry } from '@/types/audit'
import {
  severityOf,
  SEVERITY_LABEL,
  type AuditSeverity,
  type SeverityFilter,
} from '@/lib/admin/auditSeverity'

/**
 * 감사 로그 severity 탭 — 디자인 핸드오프 2026-05-10 admin.jsx §AdminAudit
 * SeverityTab (L738~741, 784~792) 1:1 매핑.
 *
 * <p>4개 탭(전체/긴급/주의/정보) — controlled. 부모가 `active` 와 `onChange` 소유.
 * 각 탭은 현재 페이지의 entries 를 기준으로 한 count 를 함께 표시한다.
 *
 * <p>count 는 현재 페이지 entries 에 한정된다 (전체 결과셋 합계는 backend severity
 * aggregation endpoint 미존재 — v1.x++). 이는 design 의 frontend-only ADMIN_AUDIT
 * 정적 배열 카운팅 동작과 동등 패턴이다.
 *
 * <p>severity 매핑은 `auditSeverity.severityOf` 사용 — frontend UX 용 분류이며
 * 보안의 진실은 backend 책임 (`@PreAuthorize`).
 *
 * <p>style: `.audit-header`, `.audit-header-spacer`, `.sev-tab`, `.sev-tab.active`,
 * `.sev-count`, `.sev-dot.sev-{info|warn|danger}` (admin.css L549~580).
 * 신규 CSS 없음 — className wiring 만.
 */
export interface SeverityTabsProps {
  active: SeverityFilter
  onChange: (next: SeverityFilter) => void
  entries: AuditLogEntry[]
  /** 우측 슬롯 (CSV/JSON export 등) — design audit-header 오른쪽 영역 */
  right?: React.ReactNode
}

export function SeverityTabs({ active, onChange, entries, right }: SeverityTabsProps) {
  const counts = countBySeverity(entries)
  const order: SeverityFilter[] = ['all', 'danger', 'warn', 'info']

  return (
    <div className="audit-header" role="tablist" aria-label="severity 필터">
      {order.map((sev) => (
        <Tab
          key={sev}
          sev={sev}
          active={active === sev}
          label={SEVERITY_LABEL[sev]}
          count={counts[sev]}
          onClick={() => onChange(sev)}
        />
      ))}
      <div className="audit-header-spacer" />
      {right}
    </div>
  )
}

function Tab({
  sev,
  active,
  label,
  count,
  onClick,
}: {
  sev: SeverityFilter
  active: boolean
  label: string
  count: number
  onClick: () => void
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      data-testid={`severity-tab-${sev}`}
      onClick={onClick}
      className={`sev-tab sev-${sev}${active ? ' active' : ''}`}
    >
      {sev !== 'all' && <span className={`sev-dot sev-${sev}`} aria-hidden="true" />}
      <span>{label}</span>
      <span className="sev-count">{count}</span>
    </button>
  )
}

interface Counts {
  all: number
  danger: number
  warn: number
  info: number
}

function countBySeverity(entries: AuditLogEntry[]): Counts {
  const counts: Counts = { all: entries.length, danger: 0, warn: 0, info: 0 }
  for (const e of entries) {
    const s: AuditSeverity = severityOf(e.eventType)
    counts[s] += 1
  }
  return counts
}
