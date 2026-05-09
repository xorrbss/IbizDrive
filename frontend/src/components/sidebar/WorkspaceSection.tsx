'use client'
import { WorkspaceFolderTree } from './WorkspaceFolderTree'

export function WorkspaceSection({
  kind, workspaceId, title, rootFolderId,
}: {
  kind: 'department' | 'team'
  workspaceId: string
  title: string
  rootFolderId: string
}) {
  return (
    <WorkspaceFolderTree
      kind={kind}
      workspaceId={workspaceId}
      rootFolderId={rootFolderId}
      rootName={title}
    />
  )
}
