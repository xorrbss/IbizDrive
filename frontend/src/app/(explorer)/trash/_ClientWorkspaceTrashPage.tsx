'use client'
import { TrashTable } from '@/components/trash/TrashTable'

/**
 * scope-aware 휴지통 공유 컴포넌트 (Plan E T8).
 * /trash/d/[deptSlug] (T9), /trash/t/[teamSlug] (T10) 양쪽 라우트 페이지가 재사용.
 *
 * - T12: TrashWorkspaceTabs 추가 예정 (props 변경 없음)
 * - T13: restoreDisabled / archived prop → TrashTable로 전달 예정
 */
export function ClientWorkspaceTrashPage(props: {
  scopeType: 'department' | 'team'
  scopeId: string
  workspaceName: string
  archived?: boolean
}) {
  const { scopeType, scopeId, workspaceName, archived = false } = props

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {/* T12에서 TrashWorkspaceTabs 추가 예정 */}
      <div className="px-6 py-4">
        <h1 className="text-2xl font-semibold">{workspaceName} 휴지통</h1>
        {archived && (
          <div
            role="alert"
            className="mt-2 rounded border border-warn/30 bg-warn/10 p-3 text-sm"
          >
            이 팀은 archive되어 콘텐츠 복원이 불가능합니다.
          </div>
        )}
      </div>
      {/* T13에서 restoreDisabled 또는 archived prop을 TrashTable로 전달 예정 */}
      <TrashTable scopeType={scopeType} scopeId={scopeId} />
    </div>
  )
}
