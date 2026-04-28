/**
 * 권한 enum mirror (docs/03 §3.1, backend Permission enum).
 *
 * 프론트에서는 UX 게이트에만 사용하고, 실제 보안 판정은 백엔드 `PermissionService`
 * / `@PreAuthorize`가 담당한다.
 */

export type PermissionCode =
  | 'READ'
  | 'UPLOAD'
  | 'EDIT'
  | 'MOVE'
  | 'DOWNLOAD'
  | 'DELETE'
  | 'SHARE'
  | 'PERMISSION_ADMIN'
  | 'PURGE'

export type PermissionPreset = 'read' | 'upload' | 'edit' | 'share' | 'admin' | 'system_admin'

export type EffectivePermissions = Record<PermissionCode, boolean>
