'use client'
import { FolderTreeNode } from './FolderTreeNode'

export function WorkspaceFolderTree({
  kind, workspaceId, rootFolderId, rootName,
}: {
  kind: 'department' | 'team'
  workspaceId: string
  rootFolderId: string
  rootName: string
}) {
  return (
    <FolderTreeNode
      section={kind}
      workspaceId={workspaceId}
      scopeType={kind}
      scopeId={workspaceId}
      folderId={rootFolderId}
      name={rootName}
      depth={0}
      pathAcc={[]}
    />
  )
}
