/**
 * 감사 로그 타입 (docs/03 §4 mirror).
 *
 * 백엔드 audit_log 테이블의 행을 클라이언트가 소비하는 형태.
 * 새 이벤트 타입을 추가할 때는 반드시 docs/03 §4.1과 동기화 (계약).
 */

export type AuditEventType =
  // 파일
  | 'file.viewed'
  | 'file.downloaded'
  | 'file.uploaded'
  | 'file.renamed'
  | 'file.moved'
  | 'file.deleted'
  | 'file.restored'
  | 'file.purged'
  // 버전
  | 'version.created'
  | 'version.restored'
  | 'version.downloaded'
  // 폴더
  | 'folder.created'
  | 'folder.renamed'
  | 'folder.moved'
  | 'folder.deleted'
  | 'folder.restored'
  | 'folder.purged'
  | 'folder.audit_level_changed'
  // 권한 / 공유
  | 'permission.granted'
  | 'permission.revoked'
  | 'permission.expired'
  | 'permission.changed'
  | 'share.created'
  | 'share.revoked'
  | 'share.expired'
  // 인증
  | 'user.registered'
  | 'user.login.success'
  | 'user.login.failed'
  | 'user.logout'
  | 'user.password.changed'
  | 'user.password.forgot_requested'
  | 'user.password.reset'
  | 'user.mfa.enabled'
  // 관리자
  | 'admin.user.created'
  | 'admin.user.updated'
  | 'admin.user.deactivated'
  | 'admin.role.changed'
  | 'admin.quota.changed'
  | 'admin.legal_hold.placed'
  | 'admin.legal_hold.released'
  | 'admin.department.created'
  | 'admin.department.updated'
  | 'admin.department.deactivated'
  | 'admin.cron.toggled'
  // 시스템
  | 'system.backup.completed'
  | 'system.purge.executed'
  | 'storage.orphan.cleaned'
  // 감사 로그 자체
  | 'audit.exported'
  // 팀
  | 'team.member.role_changed'

export type AuditResourceType = 'file' | 'folder' | 'user' | 'permission' | 'share' | 'system' | 'audit' | 'department' | 'team'

export interface AuditLogEntry {
  /** UUID. 백엔드는 audit_log.id (BIGINT) → 노출 시 hashId로 변환 권장이나 v1.0 mock은 string. */
  id: string
  /** ISO 8601 UTC. */
  occurredAt: string
  eventType: AuditEventType
  actorId: string
  actorName: string
  /** null 가능: system 이벤트 (system.backup.completed 등). */
  resourceType: AuditResourceType | null
  resourceId: string | null
  resourceName: string | null
  /** IPv4/IPv6 string 또는 null (시스템 이벤트). */
  ip: string | null
  /** before/after diff 또는 자유 형식 컨텍스트. UI는 v1.0에서 raw JSON 표시. */
  metadata: Record<string, unknown> | null
}

export interface AuditLogFilters {
  /** ISO 날짜 (YYYY-MM-DD). 둘 다 inclusive. */
  fromDate?: string
  toDate?: string
  /** actorName partial match (대소문자 구분 없음). */
  actorQuery?: string
  /** 단일 이벤트 타입 또는 비어있으면 전체. */
  eventType?: AuditEventType | ''
}

export interface AuditLogPage {
  entries: AuditLogEntry[]
  total: number
  page: number
  pageSize: number
}
