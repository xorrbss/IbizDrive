/**
 * Frontend error code constants (docs/02 §8).
 * 값은 backend GlobalExceptionHandler에서 wire되는 문자열과 일치해야 함.
 */

// 입력/검증
export const VALIDATION_ERROR = 'VALIDATION_ERROR'

// 인증/권한
export const UNAUTHORIZED = 'UNAUTHORIZED'
export const PERMISSION_DENIED = 'PERMISSION_DENIED'

// 리소스
export const NOT_FOUND = 'NOT_FOUND'

// 이동/폴더
export const MOVE_INTO_SELF = 'MOVE_INTO_SELF'
export const MOVE_INTO_DESCENDANT = 'MOVE_INTO_DESCENDANT'
export const TARGET_NOT_FOUND = 'TARGET_NOT_FOUND'

// 파일/폴더 이름 충돌
export const UPLOAD_CONFLICT = 'UPLOAD_CONFLICT'
export const RENAME_CONFLICT = 'RENAME_CONFLICT'

// 복원
export const RESTORE_CONFLICT = 'RESTORE_CONFLICT'

// 버전
export const VERSION_CONFLICT = 'VERSION_CONFLICT'

// 부서
export const DEPARTMENT_CONFLICT = 'DEPARTMENT_CONFLICT'

// 저장소
export const QUOTA_EXCEEDED = 'QUOTA_EXCEEDED'
export const FILE_TOO_LARGE = 'FILE_TOO_LARGE'

// 미디어 타입
export const UNSUPPORTED_MEDIA_TYPE = 'UNSUPPORTED_MEDIA_TYPE'

// 계정/파일 잠금
export const ACCOUNT_LOCKED = 'ACCOUNT_LOCKED'
export const FILE_LOCKED = 'FILE_LOCKED'

// Legal Hold
export const LEGAL_HOLD_VIOLATION = 'LEGAL_HOLD_VIOLATION'
export const LEGAL_HOLD_RECENTLY_RELEASED = 'LEGAL_HOLD_RECENTLY_RELEASED'

// Approval
export const APPROVAL_REQUIRED = 'APPROVAL_REQUIRED'
export const APPROVAL_SELF = 'APPROVAL_SELF'
export const APPROVAL_NOT_FOUND = 'APPROVAL_NOT_FOUND'
export const APPROVAL_ALREADY_DECIDED = 'APPROVAL_ALREADY_DECIDED'

// 팀
export const TEAM_OWNER_REQUIRED = 'TEAM_OWNER_REQUIRED'
export const TEAM_ARCHIVED = 'TEAM_ARCHIVED'

// 공유 (team-centric-pivot)
export const SHARE_EXCEEDS_MEMBER = 'SHARE_EXCEEDS_MEMBER'
export const PERMISSION_CONFLICT = 'PERMISSION_CONFLICT'

// Rate limit
export const RATE_LIMIT_EXCEEDED = 'RATE_LIMIT_EXCEEDED'

// 서버
export const INTERNAL_ERROR = 'INTERNAL_ERROR'
export const SERVICE_UNAVAILABLE = 'SERVICE_UNAVAILABLE'

/**
 * 모든 가능한 에러 코드 union type (docs/02 §8).
 * 상수 추가 시 이곳도 함께 업데이트.
 */
export type ErrorCode =
  | typeof VALIDATION_ERROR
  | typeof UNAUTHORIZED
  | typeof PERMISSION_DENIED
  | typeof NOT_FOUND
  | typeof MOVE_INTO_SELF
  | typeof MOVE_INTO_DESCENDANT
  | typeof TARGET_NOT_FOUND
  | typeof UPLOAD_CONFLICT
  | typeof RENAME_CONFLICT
  | typeof RESTORE_CONFLICT
  | typeof VERSION_CONFLICT
  | typeof DEPARTMENT_CONFLICT
  | typeof QUOTA_EXCEEDED
  | typeof FILE_TOO_LARGE
  | typeof UNSUPPORTED_MEDIA_TYPE
  | typeof ACCOUNT_LOCKED
  | typeof FILE_LOCKED
  | typeof LEGAL_HOLD_VIOLATION
  | typeof LEGAL_HOLD_RECENTLY_RELEASED
  | typeof APPROVAL_REQUIRED
  | typeof APPROVAL_SELF
  | typeof APPROVAL_NOT_FOUND
  | typeof APPROVAL_ALREADY_DECIDED
  | typeof TEAM_OWNER_REQUIRED
  | typeof TEAM_ARCHIVED
  | typeof SHARE_EXCEEDS_MEMBER
  | typeof PERMISSION_CONFLICT
  | typeof RATE_LIMIT_EXCEEDED
  | typeof INTERNAL_ERROR
  | typeof SERVICE_UNAVAILABLE
