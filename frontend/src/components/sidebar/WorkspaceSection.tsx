'use client'
import Link from 'next/link'
import { Settings } from 'lucide-react'
import { WorkspaceFolderTree } from './WorkspaceFolderTree'

export function WorkspaceSection({
  kind, workspaceId, title, rootFolderId, archived = false,
}: {
  kind: 'department' | 'team'
  workspaceId: string
  title: string
  rootFolderId: string
  /**
   * Visual archived state — dim + read-only indicator.
   * Currently always false: backend `findForUser` returns only active workspaces.
   * Hook is ready for Plan A2 (team archive endpoint) when backend starts
   * including archived entries with `archivedAt` set.
   */
  archived?: boolean
}) {
  return (
    <div className={archived ? 'opacity-60 group' : 'group'}>
      {archived && (
        <span aria-label="보관됨" className="ml-2 text-[11px] text-fg-muted font-medium">
          [보관됨]
        </span>
      )}
      {kind === 'team' && !archived && (
        <Link
          href={`/t/${workspaceId}/settings/members`}
          className="float-right opacity-0 group-hover:opacity-100 focus-visible:opacity-100 w-6 h-6 inline-flex items-center justify-center rounded text-fg-muted hover:text-fg hover:bg-surface-2 transition-colors"
          aria-label={`${title} 팀 설정`}
          title={`${title} 팀 설정`}
        >
          <Settings size={13} aria-hidden />
        </Link>
      )}
      <WorkspaceFolderTree
        kind={kind}
        workspaceId={workspaceId}
        rootFolderId={rootFolderId}
        rootName={title}
      />
    </div>
  )
}
