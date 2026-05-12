/**
 * 감사 이벤트 severity UI helper — backend `audit_log.severity` (V19) 합류 후 type 과
 * label 만 보유 (graceful upgrade 완성, P3 — Audit severity backend).
 *
 * <p>이전에는 `severityOf(eventType)` 가 `AuditEventType` 을 frontend 매핑표로 분류했으나,
 * backend `AuditSeverityMapper` 가 단일 진실이 되었고 wire 응답에 severity 가 포함된다.
 * 컴포넌트는 `entry.severity` 를 직접 사용한다.
 *
 * <p>본 모듈에 남은 책임: SeverityTab 의 'all' 필터 상태 타입과 한글 라벨 매핑.
 */

import type { AuditSeverity } from '@/types/audit'

export type { AuditSeverity } from '@/types/audit'

/** SeverityTab filter 값 — UI 상태(전체 포함). */
export type SeverityFilter = 'all' | AuditSeverity

/** 한글 라벨 — SeverityTab 표시용. */
export const SEVERITY_LABEL: Record<SeverityFilter, string> = {
  all: '전체',
  danger: '긴급',
  warn: '주의',
  info: '정보',
}
