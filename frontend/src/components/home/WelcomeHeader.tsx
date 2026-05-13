'use client'
import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'

/**
 * User Home Dashboard ① — 환영 헤더.
 *
 * <p>사용자 이름 + 기본 workspace 메타. workspace 0 zero-state 안내 (부서 배정 대기).
 *
 * <p>quick action (업로드 / 새 폴더) 은 v1.x — 본 트랙에선 미렌더. follow-up PR 에서 router wiring 후
 * default workspace 결정 (부서 → 첫 팀).
 */
export function WelcomeHeader() {
  const { data: session } = useMe()
  const { data: workspaces } = useWorkspaces()

  const name = session?.user?.name ?? '사용자'
  const department = workspaces?.department?.name
  const teamCount = workspaces?.teams?.length ?? 0
  const hasWorkspace = !!department || teamCount > 0

  return (
    <div className="flex items-end justify-between">
      <div>
        <h1 className="text-[20px] font-semibold text-fg mb-1">
          안녕하세요, {name}님
        </h1>
        {hasWorkspace ? (
          <p className="text-[13px] text-fg-2">
            {department ?? '소속 부서 없음'}
            {teamCount > 0 && ` · 팀 ${teamCount}개`}
          </p>
        ) : (
          <p className="text-[13px] text-fg-2">
            아직 소속된 workspace 가 없습니다. 관리자에게 부서 배정을 요청하거나, 팀을 만드세요.
          </p>
        )}
      </div>
    </div>
  )
}
