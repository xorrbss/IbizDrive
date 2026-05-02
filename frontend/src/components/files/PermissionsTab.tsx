'use client'
import { usePermission } from '@/hooks/usePermission'
import { PERMISSIONS, type Permission } from '@/types/permission'

/**
 * RightPanel `permissions` 탭 본문 (M-RP.3).
 *
 * - 호출자(RightPanel)가 `tab === 'permissions'`일 때만 mount → 비활성 탭에서 fetch 차단.
 * - read-only: grant/revoke UI는 본 트랙 범위 외 (m8-permission-ui 트랙).
 * - 로딩 중에는 usePermission이 모든 플래그 false 반환 → 모든 chip이 dim 상태로 시작 후
 *   서버 응답이 오면 보유 chip만 강조로 전환. conservative default(권한 없음 가정)는
 *   docs/01 §14.2와 일치하므로 별도 skeleton/error UI 미도입 (KISS).
 */
export function PermissionsTab({ fileId }: { fileId: string }) {
  const flags = usePermission(fileId)
  return (
    <ul
      aria-label="파일 권한 목록"
      className="flex flex-wrap gap-1.5"
    >
      {PERMISSIONS.map((p) => (
        <PermissionChip key={p} permission={p} held={flags[p]} />
      ))}
    </ul>
  )
}

/** 권한 enum → 한글 label. backend Permission 이름과 분리된 표시용. */
const LABELS: Record<Permission, string> = {
  READ: '읽기',
  UPLOAD: '업로드',
  EDIT: '수정',
  MOVE: '이동',
  DOWNLOAD: '다운로드',
  DELETE: '삭제',
  SHARE: '공유',
  PERMISSION_ADMIN: '권한 관리',
  PURGE: '영구 삭제',
}

function PermissionChip({
  permission,
  held,
}: {
  permission: Permission
  held: boolean
}) {
  return (
    <li
      data-permission={permission}
      data-held={held ? 'true' : 'false'}
      aria-label={`${LABELS[permission]} 권한 ${held ? '보유' : '미보유'}`}
      className={
        held
          ? 'text-[11px] px-2 py-1 rounded border border-accent/40 bg-accent/15 text-accent'
          : 'text-[11px] px-2 py-1 rounded border border-border bg-surface-2 text-fg-muted opacity-60'
      }
    >
      {LABELS[permission]}
    </li>
  )
}
