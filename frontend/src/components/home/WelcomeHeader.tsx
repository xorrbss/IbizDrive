'use client'
import Link from 'next/link'
import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { buildWorkspacePath } from '@/lib/workspacePath'

/**
 * User Home Dashboard ① — 환영 헤더.
 *
 * <p>사용자 이름 + 기본 workspace 메타 + "내 워크스페이스 →" quick link (default workspace root 진입).
 * workspace 0 zero-state 안내 (부서 배정 대기) 시 link hidden.
 *
 * <p>default workspace: 부서 우선 → 없으면 첫 팀. 둘 다 없으면 link 미렌더.
 *
 * <p>업로드 / 새 폴더 별도 quick action 은 KISS 결정으로 단순 navigation 으로 통합 (PR
 * #246 spec §3.1 의 두 버튼 안은 explorer 측 ?action= query handler 도입 필요로 scope 큼,
 * follow-up 트랙으로 분리).
 */
export function WelcomeHeader() {
  const { data: session } = useMe()
  const { data: workspaces } = useWorkspaces()

  const name = session?.user?.name ?? '사용자'
  const department = workspaces?.department
  const firstTeam = workspaces?.teams?.[0]
  const teamCount = workspaces?.teams?.length ?? 0
  const hasWorkspace = !!department || teamCount > 0

  const defaultWorkspaceLink = department
    ? buildWorkspacePath(
        { kind: 'department', workspaceId: department.id },
        department.rootFolderId,
        [],
      )
    : firstTeam
    ? buildWorkspacePath(
        { kind: 'team', workspaceId: firstTeam.id },
        firstTeam.rootFolderId,
        [],
      )
    : null

  return (
    <div className="flex items-end justify-between gap-4">
      <div>
        <h1 className="text-[20px] font-semibold text-fg mb-1">
          안녕하세요, {name}님
        </h1>
        {hasWorkspace ? (
          <p className="text-[13px] text-fg-2">
            {department?.name ?? '소속 부서 없음'}
            {teamCount > 0 && ` · 팀 ${teamCount}개`}
          </p>
        ) : (
          <p className="text-[13px] text-fg-2">
            아직 소속된 workspace 가 없습니다. 관리자에게 부서 배정을 요청하거나, 팀을 만드세요.
          </p>
        )}
      </div>
      {defaultWorkspaceLink && (
        <Link
          href={defaultWorkspaceLink}
          className="text-[13px] text-fg-2 hover:text-fg shrink-0"
        >
          내 워크스페이스 →
        </Link>
      )}
    </div>
  )
}
