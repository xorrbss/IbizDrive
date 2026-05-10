'use client'
import { notFound } from 'next/navigation'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { ClientWorkspaceTrashPage } from '@/app/(explorer)/trash/_ClientWorkspaceTrashPage'

/**
 * Client wrapper: useWorkspaces에서 teams[] 배열을 slug(=UUID MVP)로 검색 후
 * ClientWorkspaceTrashPage에 props 전달. 미매칭 → notFound().
 *
 * archived team도 매칭 정상 — listing read-only로 진입 가능.
 * MVP: slug = workspace UUID (workspacePath.ts 주석 참조).
 * 추후 SEO slug + server lookup 도입 시 server-side resolver로 교체 가능.
 */
export function ClientTeamTrashWrapper({ teamSlug }: { teamSlug: string }) {
  const { data } = useWorkspaces()

  // 로딩 중: data가 undefined → 렌더 보류 (TanStack Query suspense 미사용, null 반환)
  if (data === undefined) return null

  const team = data.teams.find((t) => t.id === teamSlug) ?? null

  if (!team) {
    notFound()
  }

  return (
    <ClientWorkspaceTrashPage
      scopeType="team"
      scopeId={team.id}
      workspaceName={team.name}
      archived={Boolean(team.archivedAt)}
    />
  )
}
