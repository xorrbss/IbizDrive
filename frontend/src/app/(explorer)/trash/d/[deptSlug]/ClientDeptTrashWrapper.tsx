'use client'
import { notFound } from 'next/navigation'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { ClientWorkspaceTrashPage } from '@/app/(explorer)/trash/_ClientWorkspaceTrashPage'

/**
 * Client wrapper: useWorkspaces에서 department를 slug(=UUID MVP)로 조회 후
 * ClientWorkspaceTrashPage에 props 전달. 미매칭 → notFound().
 *
 * MVP: slug = workspace UUID (workspacePath.ts 주석 참조).
 * 추후 SEO slug + server lookup 도입 시 server-side resolver로 교체 가능.
 */
export function ClientDeptTrashWrapper({ deptSlug }: { deptSlug: string }) {
  const { data } = useWorkspaces()

  // 로딩 중: data가 undefined → 렌더 보류 (TanStack Query suspense 미사용, null 반환)
  if (data === undefined) return null

  const dept = data.department?.id === deptSlug ? data.department : null

  if (!dept) {
    notFound()
  }

  return (
    <ClientWorkspaceTrashPage
      scopeType="department"
      scopeId={dept.id}
      workspaceName={dept.name}
      archived={Boolean(dept.archivedAt)}
    />
  )
}
