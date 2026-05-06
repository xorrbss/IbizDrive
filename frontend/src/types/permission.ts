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

/**
 * Admin 권한 매트릭스 (Wave 2 T5) — `GET /api/admin/permissions` row.
 *
 * <p>{@link PermissionListItem}와 분리된 타입 — 백엔드 {@code AdminPermissionRowResponse} 미러.
 * 차이점:
 * <ul>
 *   <li>subjectType: `role` 포함 (V5 schema artifact, MVP 평가 미사용 — docs/03 §3.4)</li>
 *   <li>resourceName / grantedByName / isExpired 추가 — 백엔드가 batch resolve + derive</li>
 *   <li>preset: 4값 (V5 CHECK — `share`는 별도 `shares` 테이블)</li>
 * </ul>
 */
export type AdminSubjectType = 'user' | 'department' | 'role' | 'everyone'

export type AdminResourceType = 'folder' | 'file'

export type AdminPreset = 'read' | 'upload' | 'edit' | 'admin'

export const ADMIN_SUBJECT_TYPES: readonly AdminSubjectType[] = ['user', 'department', 'role', 'everyone'] as const
export const ADMIN_RESOURCE_TYPES: readonly AdminResourceType[] = ['folder', 'file'] as const
export const ADMIN_PRESETS: readonly AdminPreset[] = ['read', 'upload', 'edit', 'admin'] as const

export type AdminPermissionRow = {
  id: string
  subjectType: AdminSubjectType
  subjectId: string | null
  subjectName: string | null
  resourceType: AdminResourceType
  resourceId: string
  resourceName: string | null
  preset: AdminPreset
  grantedByActorId: string
  grantedByName: string | null
  grantedAt: string
  expiresAt: string | null
  isExpired: boolean
}

/**
 * Admin 권한 매트릭스 filter — 모두 optional.
 * UI 측 빈 문자열 / undefined 는 query string에서 skip (api 레이어 처리).
 */
export type AdminPermissionFilters = {
  subjectType?: AdminSubjectType
  subjectId?: string
  resourceType?: AdminResourceType
  preset?: AdminPreset
  q?: string
  page?: number
  size?: number
}

/** Spring {@code Page<AdminPermissionRowResponse>} 직렬화 모양. */
export interface AdminPermissionPage {
  content: AdminPermissionRow[]
  totalElements: number
  totalPages: number
  number: number
  size: number
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
