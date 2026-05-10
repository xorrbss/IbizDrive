/**
 * 백엔드 API 에러 envelope code 상수 + 토스트 메시지 매퍼.
 *
 * <p>{@code docs/02 §8} 에러 코드 표가 진실의 출처. wire format은 spec 표기({@code ERR_*}) 접두사를
 * 제거한 형태 — 예: spec {@code ERR_TEAM_ARCHIVED} ↔ wire {@code TEAM_ARCHIVED}. backend
 * {@code GlobalExceptionHandler}가 매핑.
 *
 * <p>frontend 호출부는 {@link api}의 {@code buildApiError}가 응답 body의 {@code error.code}를
 * {@code Error.code} 필드로 보존한다 (그리고 {@code .status}). 본 모듈의 {@link getApiErrorCode}로
 * 추출, {@link messageForError}로 사용자용 메시지 매핑.
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
export const RESTORE_CONFLICT = 'RESTORE_CONFLICT'
export const VERSION_CONFLICT = 'VERSION_CONFLICT'
export const DEPARTMENT_CONFLICT = 'DEPARTMENT_CONFLICT'

// 저장소
export const QUOTA_EXCEEDED = 'QUOTA_EXCEEDED'
export const FILE_TOO_LARGE = 'FILE_TOO_LARGE'
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

// Rate / 서버
export const RATE_LIMIT_EXCEEDED = 'RATE_LIMIT_EXCEEDED'
export const INTERNAL_ERROR = 'INTERNAL_ERROR'
export const SERVICE_UNAVAILABLE = 'SERVICE_UNAVAILABLE'

/**
 * 모든 envelope code union — 상수 추가 시 여기도 동기화.
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

/**
 * {@code buildApiError}가 부여한 {@code Error.code}를 안전 추출.
 * code가 없거나 Error가 아니면 null. envelope 외 generic Error 또는 network error에서 null 반환.
 */
export function getApiErrorCode(err: unknown): string | null {
  if (err && typeof err === 'object' && 'code' in err) {
    const code = (err as { code?: unknown }).code
    return typeof code === 'string' ? code : null
  }
  return null
}

/**
 * 알려진 envelope code → 사용자용 한국어 토스트 메시지 매퍼.
 *
 * <p>매퍼에 등록된 code는 명시 메시지를 반환, 미등록은 fallback. 새 code 추가 시:
 * 1. 위 상수에 추가 + ErrorCode union에 추가
 * 2. {@link MESSAGES} 테이블에 메시지 등록 (사용자 노출 의미가 있을 때만)
 * 3. docs/02 §8과 동기화 확인 (CLAUDE.md §3 원칙 12)
 *
 * <p>현재 등록된 code: TEAM_ARCHIVED (PR #141 enforcement). 다른 code들은 호출부의 기존 메시지가
 * 충분하거나 별도 다이얼로그(RenameConflict, RestoreConflict 등)로 처리되므로 매퍼 미등록.
 */
const MESSAGES: Partial<Record<string, string>> = {
  [TEAM_ARCHIVED]: 'archive된 팀의 콘텐츠는 수정할 수 없습니다',
}

/**
 * 에러를 사용자용 토스트 메시지로 변환. 매퍼 등록 code면 그 메시지, 아니면 fallback.
 *
 * @param err React Query mutation onError가 받는 unknown error
 * @param fallback 매퍼 미등록 시 사용할 default 메시지 (예: "이동에 실패했습니다. 다시 시도해 주세요.")
 */
export function messageForError(err: unknown, fallback: string): string {
  const code = getApiErrorCode(err)
  if (code && MESSAGES[code]) {
    return MESSAGES[code] as string
  }
  return fallback
}
