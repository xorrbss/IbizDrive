'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useWorkspaces } from '@/hooks/useWorkspaces'

/**
 * /trash 진입 — workspace-aware redirect handler (Plan E T11).
 *
 * 휴지통은 scope-bound (T1~T2: TrashController scope param 필수). /trash 자체는 scope를
 * 결정할 수 없으므로, 사용자의 workspace 멤버십에 따라 /trash/d/:id 또는 /trash/t/:id 로
 * router.replace한다. 미배정 사용자는 EmptyWorkspacesState 노출 (admin 액션 대기).
 *
 * 우선순위: department > 첫 활성 팀 > 첫 팀(archived fallback). `app/page.tsx` 와 동일 패턴.
 */
export default function TrashRedirectPage() {
  const router = useRouter()
  const { data, isLoading } = useWorkspaces()

  useEffect(() => {
    if (!data) return
    const dept = data.department
    if (dept) {
      router.replace(`/trash/d/${dept.id}`)
      return
    }
    const firstActiveTeam = data.teams.find((t) => !t.archivedAt) ?? data.teams[0]
    if (firstActiveTeam) {
      router.replace(`/trash/t/${firstActiveTeam.id}`)
      return
    }
    // workspace 0 → EmptyWorkspacesState 분기 (아래 render에서 처리)
  }, [data, router])

  if (isLoading) {
    return <div className="p-6 text-[13px] text-fg-muted">로딩 중...</div>
  }
  if (data && data.department === null && data.teams.length === 0) {
    return <EmptyWorkspacesState />
  }
  // redirect 진행 중 — 빈 화면. router.replace 가 즉시 트리거됨.
  return null
}

function EmptyWorkspacesState() {
  return (
    <div
      role="status"
      className="flex flex-1 flex-col items-center justify-center gap-3 py-[60px]"
    >
      <p className="text-fg-muted">참여 중인 workspace가 없어 휴지통에 접근할 수 없습니다.</p>
      <p className="text-sm text-fg-muted">관리자에게 문의해 주세요.</p>
    </div>
  )
}
