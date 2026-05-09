'use client'
import Link from 'next/link'
import { useFolderChildren } from '@/hooks/useFolderChildren'
import { useSidebarTreeStore } from '@/stores/sidebarTree'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildWorkspacePath, type SidebarSectionKind } from '@/lib/workspacePath'

interface Props {
  section: SidebarSectionKind
  workspaceId: string  // department/team id; section==='shared'에선 호출되지 않음
  scopeType: 'department' | 'team'
  scopeId: string
  folderId: string
  name: string
  depth: number
  pathAcc: string[]
}

export function FolderTreeNode({
  section, workspaceId, scopeType, scopeId, folderId, name, depth, pathAcc,
}: Props) {
  const { expandedFolderIds, toggleFolder } = useSidebarTreeStore()
  const { folderId: activeId } = useCurrentFolder()

  const isExpanded = expandedFolderIds.includes(folderId)
  const isActive = activeId === folderId

  const children = useFolderChildren(scopeType, scopeId, folderId, { enabled: isExpanded })

  const loc =
    section === 'shared'
      ? { kind: 'shared' as const }
      : { kind: section, workspaceId }
  const href = buildWorkspacePath(loc, folderId, pathAcc)

  return (
    <div>
      <div
        className={`flex items-center gap-1.5 px-2 py-1 rounded min-h-[26px] transition-colors ${
          isActive
            ? 'bg-accent-soft text-accent font-medium'
            : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
        }`}
        style={{ paddingLeft: depth * 12 + 8 }}
      >
        <button
          onClick={() => toggleFolder(folderId)}
          aria-label={isExpanded ? '접기' : '펼치기'}
          aria-expanded={isExpanded}
          className="w-3.5 inline-flex items-center justify-center text-fg-muted text-[10px]"
        >
          {isExpanded ? '▾' : '▸'}
        </button>
        <Link href={href} className="flex-1 truncate text-inherit">
          📁 {name}
        </Link>
      </div>
      {isExpanded && children.data?.map((c) => (
        <FolderTreeNode
          key={c.id}
          section={section}
          workspaceId={workspaceId}
          scopeType={scopeType}
          scopeId={scopeId}
          folderId={c.id}
          name={c.name}
          depth={depth + 1}
          pathAcc={[...pathAcc, c.slug]}
        />
      ))}
      {isExpanded && children.isLoading && (
        <div className="px-2 py-0.5 text-[11px] text-fg-muted" style={{ paddingLeft: (depth + 1) * 12 + 8 }}>
          로딩…
        </div>
      )}
    </div>
  )
}
