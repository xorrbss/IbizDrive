// frontend/src/types/permission.ts
// 설계: docs/01 §14 (권한 기반 조건부 렌더링), docs/03 §3 (권한 매트릭스 — 미정)
//
// 백엔드 매트릭스가 확정되지 않아 v1 enum은 docs/01 §14.2의 8가지로 고정.
// 매트릭스 확정 시 본 enum과 docs/03 §3을 함께 갱신해야 함 (CLAUDE.md §3 원칙 12).

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

/**
 * 권한 탭 표시용 grant — 노드에 부여된 사용자/그룹과 역할 묶음.
 *
 * MVP에선 화면 표시만(read-only). 부여/회수 UX는 v1.x.
 */
export type PermissionRole = 'owner' | 'editor' | 'viewer'

export type PermissionGrant = {
  id: string
  subjectType: 'user' | 'group'
  subjectName: string
  role: PermissionRole
  inherited: boolean // 상속(상위 폴더에서 받은) 여부
}
