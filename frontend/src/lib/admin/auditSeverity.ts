/**
 * Audit event severity 매핑 — 디자인 핸드오프 2026-05-10 admin.jsx
 * §AdminAudit SeverityTab + audit-stream (L711~782) frontend-only 분류.
 *
 * <p>backend `audit_log` 테이블에는 severity 컬럼이 없으므로 (docs/03 §4) 본
 * 모듈은 frontend 에서 `AuditEventType` → 세 단계 severity 로 매핑한다. backend
 * 합류 시 컬럼 추가되면 본 helper 는 그대로 두고 entry 가 severity 를 직접
 * 노출하면 우선한다 (graceful upgrade).
 *
 * <p>severity 분류 기준 (design data.js ADMIN_AUDIT 의 severity 패턴 참조):
 * <ul>
 *   <li><code>danger</code>: 외부 노출 가능성/공개 링크/계정 잠금 등 정책 위반
 *     가능성. 운영자가 즉시 검토해야 하는 이벤트.</li>
 *   <li><code>warn</code>: 외부 도메인 공유 / 인증 실패 / 권한 회수 등 검토
 *     대상이지만 즉시 위험은 아닌 이벤트.</li>
 *   <li><code>info</code>: 일상 운영 이벤트 (업로드/다운로드/이름 변경 등).
 *     명시 매핑 없는 이벤트 타입의 기본값.</li>
 * </ul>
 *
 * <p>본 분류는 frontend UX 용이며 보안의 진실은 아니다 — 정책 알람/통보는
 * backend 책임 (v1.x++ alerting 트랙).
 */

import type { AuditEventType } from '@/types/audit'

export type AuditSeverity = 'info' | 'warn' | 'danger'

/** 명시 매핑 — 본 표에 없는 event type 은 'info' 로 fallback. */
const SEVERITY_MAP: Partial<Record<AuditEventType, AuditSeverity>> = {
  // danger — 정책 위반 가능성 / 즉시 검토 필요
  'share.created': 'danger', // 외부 공유 (design share.public 대응)
  'user.login.failed': 'warn', // 5회 실패는 design 상 danger 지만 단건은 warn
  // warn — 외부 도메인 / 권한 회수 / 권한 만료 / 백업 실패 등
  'permission.revoked': 'warn',
  'permission.expired': 'warn',
  'share.revoked': 'warn',
  'share.expired': 'warn',
  'admin.legal_hold.placed': 'warn',
  'admin.legal_hold.released': 'warn',
  'admin.user.deactivated': 'warn',
  'admin.role.changed': 'warn',
  'admin.cron.toggled': 'warn',
  'file.deleted': 'warn',
  'file.purged': 'warn',
  'folder.deleted': 'warn',
  'folder.purged': 'warn',
  'team.archived': 'warn',
  'system.purge.executed': 'warn',
}

export function severityOf(eventType: AuditEventType): AuditSeverity {
  return SEVERITY_MAP[eventType] ?? 'info'
}

/** SeverityTab filter 값 — UI 상태(전체 포함). */
export type SeverityFilter = 'all' | AuditSeverity

/** 한글 라벨 — SeverityTab 표시용. */
export const SEVERITY_LABEL: Record<SeverityFilter, string> = {
  all: '전체',
  danger: '긴급',
  warn: '주의',
  info: '정보',
}
