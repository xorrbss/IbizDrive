'use client'
import { useResourcePermissions } from '@/hooks/useResourcePermissions'
import { usePermission } from '@/hooks/usePermission'
import type { PermissionListItem, Preset } from '@/types/permission'

/**
 * 리소스에 부여된 grant 목록 — RightPanel 권한 탭의 PermissionsTab 하위 (M8.1).
 *
 * BE `GET /api/{folders|files}/:id/permissions` (docs/02 §7.10) 결과를 read-only 로 노출.
 * grant/revoke UI 는 후속 트랙. PERMISSION_ADMIN 보유자에게만 보임 — 미보유자에게는
 * 컴포넌트 자체가 null (UX 조건부 렌더, 보안은 BE `@PreAuthorize` 가 책임 — 원칙 #10).
 *
 * UI 패턴은 A16 SharesTable (`role="grid"` + aria-rowcount/rowindex + 4상태) 동형.
 * 가상화 미적용 — BE 가 단일 리소스의 grant 수가 작다는 가정 (페이지네이션 X).
 */
const GRID_COLS = 'grid grid-cols-[1fr_120px_140px_140px] gap-3 items-center px-3'

export function ResourcePermissionsList({
  resourceType,
  id,
}: {
  resourceType: 'folder' | 'file'
  id: string
}) {
  const flags = usePermission(id)
  const isAdmin = flags.PERMISSION_ADMIN
  const query = useResourcePermissions(resourceType, id, { enabled: isAdmin })

  // PERMISSION_ADMIN 미보유 — 섹션 자체를 숨김 (UX 가드).
  if (!isAdmin) return null

  return (
    <section
      aria-label="권한 부여 목록"
      className="mt-4 border-t border-border pt-3"
    >
      <h3 className="px-3 text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted mb-2">
        부여된 권한
      </h3>
      <ResourcePermissionsBody query={query} />
    </section>
  )
}

/**
 * 4상태 분기 — loading/error/empty/data. 본문만 분리하여 admin 가드 코드 흐름과 격리.
 * SharesTable 의 4상태 렌더 패턴 mirror — 메시지 톤만 권한 도메인에 맞춤.
 */
function ResourcePermissionsBody({
  query,
}: {
  query: ReturnType<typeof useResourcePermissions>
}) {
  if (query.isLoading) {
    return (
      <div role="status" aria-live="polite" className="px-3 py-3 text-[12px] text-fg-muted">
        로딩…
      </div>
    )
  }
  if (query.isError) {
    return (
      <div role="alert" className="px-3 py-3 text-[12px] text-danger">
        권한 목록을 불러올 수 없습니다.
      </div>
    )
  }
  const items = query.data ?? []
  if (items.length === 0) {
    return (
      <div role="status" className="px-3 py-3 text-[12px] text-fg-muted">
        부여된 권한이 없습니다.
      </div>
    )
  }
  return (
    <div
      role="grid"
      aria-rowcount={items.length + 1}
      aria-label="권한 부여 목록 표"
      className="flex flex-col"
    >
      <div
        role="row"
        aria-rowindex={1}
        className={`${GRID_COLS} h-[26px] bg-surface-1 border-y border-border text-[10px] uppercase tracking-[0.04em] font-medium text-fg-muted`}
      >
        <span role="columnheader">대상</span>
        <span role="columnheader">권한</span>
        <span role="columnheader">부여일</span>
        <span role="columnheader">만료</span>
      </div>
      {items.map((it, idx) => (
        <div
          key={it.id}
          role="row"
          aria-rowindex={idx + 2}
          data-permission-id={it.id}
          className={`${GRID_COLS} h-[36px] border-b border-border text-[12px] hover:bg-surface-2`}
        >
          <span role="gridcell" className="truncate">
            {subjectDisplay(it)}
          </span>
          <span role="gridcell" className="text-fg-muted">
            {presetLabel(it.preset)}
          </span>
          <span role="gridcell" className="text-fg-muted">
            {formatDate(it.createdAt)}
          </span>
          <span role="gridcell" className="text-fg-muted">
            {it.expiresAt ? formatDate(it.expiresAt) : '없음'}
          </span>
        </div>
      ))}
    </div>
  )
}

/**
 * 주체 표시 fallback 정책:
 * 1) backend 가 resolve 한 `subjectName` 우선 (user display_name / department name)
 * 2) `everyone` grant → `'전체'`
 * 3) name 미해결 (soft-delete 등) → `'(미해결)' + subjectId` (raw UUID — A16 SharesTable 답습)
 */
function subjectDisplay(it: PermissionListItem): string {
  if (it.subjectName) return it.subjectName
  if (it.subjectType === 'everyone') return '전체'
  return `(미해결) ${it.subjectId ?? ''}`
}

function presetLabel(p: Preset): string {
  switch (p) {
    case 'read':
      return '읽기'
    case 'upload':
      return '업로드'
    case 'edit':
      return '편집'
    case 'share':
      return '공유'
    case 'admin':
      return '관리'
  }
}

function formatDate(iso: string): string {
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return iso
    return d.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}
