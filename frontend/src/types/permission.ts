/**
 * 권한 enum (docs/03 §3.1~§3.2 mirror, ADR #17).
 *
 * 백엔드 `com.ibizdrive.permission.Permission` / `Preset`의 1:1 미러.
 * 프론트 사용처는 UX 게이트(메뉴 disable 등) 한정 — 보안 boundary 아님 (CLAUDE.md §3 원칙 10).
 *
 * 변경 시 양쪽 동시 갱신 (계약 — CLAUDE.md §4 계약 파일 표).
 */

/** 9 권한 — 백엔드 Permission enum 이름과 동일한 UPPER_SNAKE_CASE. */
export type Permission =
  | 'READ'
  | 'UPLOAD'
  | 'EDIT'
  | 'MOVE'
  | 'DOWNLOAD'
  | 'DELETE'
  | 'SHARE'
  | 'PERMISSION_ADMIN'
  | 'PURGE'

export const PERMISSIONS: readonly Permission[] = [
  'READ',
  'UPLOAD',
  'EDIT',
  'MOVE',
  'DOWNLOAD',
  'DELETE',
  'SHARE',
  'PERMISSION_ADMIN',
  'PURGE',
] as const

/** 5 preset — 백엔드 Preset enum의 wire format(lowercase)과 동일. */
export type Preset = 'read' | 'upload' | 'edit' | 'share' | 'admin'

export const PRESETS: readonly Preset[] = ['read', 'upload', 'edit', 'share', 'admin'] as const

/**
 * Preset → Permission 집합 매핑 (docs/03 §3.2 표).
 *
 * `PURGE`는 어떤 preset에도 포함되지 않는다 (시스템 ROLE ADMIN 한정 — docs/03 line 334).
 * "DELETE (자기 것)" 등 세부 조건은 백엔드 service 레벨 — 본 매핑은 권한 enum 보유 여부만.
 */
/**
 * Subject 종류 — V5 `permissions.subject_type` CHECK 제약과 동일.
 * `everyone` grant 는 `subject_id IS NULL` (DTO `subjectId === null`).
 */
export type SubjectType = 'user' | 'department' | 'everyone'

/**
 * `GET /api/{folders|files}/:id/permissions` 응답 row (M8.1).
 *
 * 백엔드 {@link com.ibizdrive.permission.dto.PermissionDto} 미러 — 변경 시 양쪽 동시 갱신
 * (CLAUDE.md §4 계약 파일 표). `subjectName` 은 backend 가 user/department batch resolve 한 표시명
 * (A16 ShareDto 동형). soft-delete / everyone / 미해결 케이스에서는 `null`.
 *
 * 본 list 는 PERMISSION_ADMIN 보유자만 호출 가능 (BE `@PreAuthorize`) — 일반 사용자에게는
 * 컴포넌트가 조건부 미렌더 (UX 가드, 보안 X).
 */
export type PermissionListItem = {
  id: string
  resourceType: 'folder' | 'file'
  resourceId: string
  subjectType: SubjectType
  subjectId: string | null
  preset: Preset
  grantedBy: string
  expiresAt: string | null
  createdAt: string
  subjectName: string | null
}

export const PRESET_PERMISSIONS: Readonly<Record<Preset, ReadonlySet<Permission>>> = {
  read: new Set<Permission>(['READ', 'DOWNLOAD']),
  upload: new Set<Permission>(['READ', 'UPLOAD', 'DOWNLOAD']),
  edit: new Set<Permission>(['READ', 'UPLOAD', 'EDIT', 'MOVE', 'DOWNLOAD', 'DELETE']),
  share: new Set<Permission>(['READ', 'UPLOAD', 'EDIT', 'MOVE', 'DOWNLOAD', 'DELETE', 'SHARE']),
  admin: new Set<Permission>([
    'READ',
    'UPLOAD',
    'EDIT',
    'MOVE',
    'DOWNLOAD',
    'DELETE',
    'SHARE',
    'PERMISSION_ADMIN',
  ]),
} as const
