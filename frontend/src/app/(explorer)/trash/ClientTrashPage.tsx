'use client'
import { TrashTable } from '@/components/trash/TrashTable'
import { RestoreConflictDialog } from '@/components/trash/RestoreConflictDialog'

/**
 * /trash 레거시 진입점 (M9.3, docs/01 §13).
 * Plan E T11에서 /trash → /trash/d/:deptSlug 또는 /trash/t/:teamSlug 리디렉트로 대체 예정.
 * T8 ClientWorkspaceTrashPage가 scope-aware 공유 컴포넌트를 제공 — 본 파일은 T11 완료 시 삭제.
 *
 * TODO: [BLOCKED]
 *   violated: 편법 금지
 *   reason: scope(scopeType/scopeId)를 URL에서 읽어야 하나 /trash 라우트는 scope 미포함.
 *   required_change: T11 redirect handler 구현 후 이 컴포넌트 제거. T9/T10이 scope-aware 라우트 제공.
 */
export function ClientTrashPage() {
  return (
    <div className="flex-1 min-w-0 flex flex-col bg-bg">
      <div className="flex items-center px-4 h-[44px] border-b border-border">
        <h1 className="text-[14px] font-semibold tracking-tight">휴지통</h1>
      </div>
      {/* scope 미확정 — T11 redirect 완료 전 disabled 상태. scopeId='' → enabled=false */}
      <TrashTable scopeType="department" scopeId="" />
      <RestoreConflictDialog />
    </div>
  )
}
