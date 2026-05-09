'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { buildWorkspacePath } from '@/lib/workspacePath'

export default function Home() {
  const router = useRouter()
  const { data, isLoading } = useWorkspaces()

  useEffect(() => {
    if (isLoading || !data) return
    if (data.department) {
      router.replace(
        buildWorkspacePath(
          { kind: 'department', workspaceId: data.department.id },
          data.department.rootFolderId,
          [],
        ),
      )
      return
    }
    const firstTeam = data.teams[0]
    if (firstTeam) {
      router.replace(
        buildWorkspacePath(
          { kind: 'team', workspaceId: firstTeam.id },
          firstTeam.rootFolderId,
          [],
        ),
      )
      return
    }
    // 미배정 + 0 teams: 사용자에게 안내 — 부서 미배정은 admin 액션 대기 상태
  }, [data, isLoading, router])

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center text-[13px] text-fg-muted">
        workspace 진입 중…
      </div>
    )
  }
  if (data && !data.department && data.teams.length === 0) {
    return (
      <div role="status" className="flex h-screen flex-col items-center justify-center gap-2 text-[13px] text-fg-muted">
        <p>아직 소속된 workspace가 없습니다.</p>
        <p>관리자에게 부서 배정을 요청하거나, 새 팀을 만드세요.</p>
      </div>
    )
  }
  return null
}
