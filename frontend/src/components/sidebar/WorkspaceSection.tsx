'use client'
export function WorkspaceSection({ title }: {
  kind: 'department' | 'team'
  workspaceId: string
  title: string
  rootFolderId: string
}) {
  return <div className="px-2 py-1">{title}</div>
}
