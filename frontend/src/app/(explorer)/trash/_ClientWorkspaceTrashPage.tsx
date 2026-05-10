'use client'
import { TrashTable } from '@/components/trash/TrashTable'
import { TrashWorkspaceTabs } from '@/components/trash/TrashWorkspaceTabs'

/**
 * scope-aware 휴지통 공유 컴포넌트 (Plan E T8).
 * /trash/d/[deptSlug] (T9), /trash/t/[teamSlug] (T10) 양쪽 라우트 페이지가 재사용.
 *
 * Plan E T13 — `archived` prop 을 TrashTable 로 forward 해 행별 복원 버튼을
 * 비활성화한다. 보안은 backend TeamArchiveGuard 가 책임 — 본 prop 은 UX 가드.
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
      <TrashWorkspaceTabs activeScope={{ type: scopeType, id: scopeId }} />
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
      <TrashTable scopeType={scopeType} scopeId={scopeId} archived={archived} />
    </div>
  )
}
