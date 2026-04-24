// frontend/src/hooks/usePermission.ts
// TODO(M7 권한): docs/01 §14.2 스펙대로 useQuery + api.getEffectivePermissions()로 교체.
// docs/03 §3 권한 매트릭스 확정 후 실제 구현 예정.

export type Permission =
  | 'read'
  | 'upload'
  | 'edit'
  | 'delete'
  | 'download'
  | 'move'
  | 'share'
  | 'admin'

export type PermissionFlags = Record<Permission, boolean>

export function usePermission(_nodeId?: string): PermissionFlags {
  return {
    read: true,
    upload: true,
    edit: true,
    delete: true,
    download: true,
    move: true,
    share: true,
    admin: true,
  }
}
