'use client'
import { useNodePermissionGrants } from '@/hooks/useNodePermissionGrants'
import type { PermissionGrant, PermissionRole } from '@/types/permission'

type Props = {
  fileId: string
}

const ROLE_LABEL: Record<PermissionRole, string> = {
  owner: '소유자',
  editor: '편집',
  viewer: '읽기',
}

const ROLE_TONE: Record<PermissionRole, string> = {
  owner: 'bg-accent-soft text-accent',
  editor: 'bg-surface-2 text-fg',
  viewer: 'bg-surface-2 text-fg-muted',
}

/**
 * RightPanel '권한' 탭 본체.
 *
 * MVP — read-only 표시. 부여/회수 UX는 v1.x.
 * 설계: docs/01 §14
 */
export function PermissionsPanel({ fileId }: Props) {
  const { data, isLoading, error } = useNodePermissionGrants(fileId)

  if (isLoading) {
    return (
      <div className="space-y-2 animate-pulse" aria-hidden>
        <div className="h-6 bg-surface-2 rounded" />
        <div className="h-6 bg-surface-2 rounded w-5/6" />
        <div className="h-6 bg-surface-2 rounded w-2/3" />
      </div>
    )
  }
  if (error) {
    return (
      <div role="alert" className="text-[12px] text-danger">
        권한 정보를 불러오지 못했습니다.
      </div>
    )
  }
  if (!data || data.length === 0) {
    return <div className="text-fg-subtle text-[12px] py-4">부여된 권한이 없습니다.</div>
  }

  return (
    <ul aria-label="권한 grant 목록" className="flex flex-col gap-1.5">
      {data.map((g) => (
        <GrantRow key={g.id} grant={g} />
      ))}
    </ul>
  )
}

function GrantRow({ grant }: { grant: PermissionGrant }) {
  return (
    <li className="flex items-center justify-between gap-2 px-2 py-1.5 rounded hover:bg-surface-2">
      <div className="flex items-center gap-2 min-w-0">
        <span
          aria-hidden
          className="w-7 h-7 inline-flex items-center justify-center rounded-full bg-surface-2 text-fg-muted text-[11px]"
        >
          {grant.subjectType === 'user' ? '👤' : '👥'}
        </span>
        <div className="flex flex-col min-w-0">
          <span className="text-[12.5px] text-fg truncate">{grant.subjectName}</span>
          {grant.inherited && (
            <span className="text-[10.5px] text-fg-subtle">상속됨</span>
          )}
        </div>
      </div>
      <span
        className={`shrink-0 px-1.5 py-0.5 rounded text-[10.5px] font-medium ${ROLE_TONE[grant.role]}`}
      >
        {ROLE_LABEL[grant.role]}
      </span>
    </li>
  )
}
