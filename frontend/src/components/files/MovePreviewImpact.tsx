'use client'
import type { MovePreviewResponse } from '@/lib/api.move'

interface Props {
  data: MovePreviewResponse
  isPending: boolean
}

/**
 * Cross-workspace 이동 영향 요약 패널.
 * MoveFolderDialog에서 destination이 다른 workspace일 때 표시.
 */
export function MovePreviewImpact({ data, isPending }: Props) {
  if (isPending) {
    return (
      <div
        data-testid="cross-workspace-warning"
        className="rounded border border-warning/40 bg-warning/5 px-3 py-2 text-[12px] text-fg-muted animate-pulse"
      >
        영향 범위 분석 중…
      </div>
    )
  }

  return (
    <div
      data-testid="cross-workspace-warning"
      className="rounded border border-warning/40 bg-warning/5 px-3 py-2 text-[12px] space-y-1"
    >
      <div className="font-medium text-warning-fg">다른 workspace로 이동</div>
      <ul className="space-y-0.5 text-fg-muted">
        <li>항목 수: {data.itemCount}개</li>
        {data.removedPermissions.length > 0 && (
          <li>제거될 권한: {data.removedPermissions.length}개</li>
        )}
        {data.revokedShares.length > 0 && (
          <li>취소될 공유: {data.revokedShares.length}개</li>
        )}
        {data.targetMembershipDefaults.length > 0 && (
          <li>
            대상 workspace 기본 권한:{' '}
            {data.targetMembershipDefaults.join(', ')}
          </li>
        )}
      </ul>
      {data.nameConflict && (
        <div className="text-danger font-medium">
          이름 충돌: "{data.nameConflict}" 항목이 이미 존재합니다
        </div>
      )}
    </div>
  )
}
