'use client'
import Link from 'next/link'
import { useFolderChildren } from '@/hooks/useFolderChildren'
import { useSidebarTreeStore } from '@/stores/sidebarTree'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildWorkspacePath, type SidebarSectionKind } from '@/lib/workspacePath'
import { useFolderDroppable } from '@/components/dnd/useFolderDroppable'

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

  const drop = useFolderDroppable(folderId, {
    kind: section === 'shared' ? 'shared' : section,
    id: section === 'shared' ? null : workspaceId,
  })

  const dropClass = !drop.isDragging
    ? ''
    : drop.isCrossWorkspace || drop.isSharedTarget
      ? 'opacity-50 cursor-not-allowed'
      : drop.isInvalid || drop.isSameFolder
        ? 'opacity-50'
        : drop.isOver
          ? 'bg-accent-soft ring-2 ring-accent'
          : ''

  const dropTitle =
    drop.isCrossWorkspace
      ? '다른 workspace로 이동 불가 (컨텍스트 메뉴를 사용하세요)'
      : drop.isSharedTarget
        ? '공유받음 영역으로 이동 불가'
        : undefined

  return (
    <div>
      <div
        ref={drop.setNodeRef}
        className={`flex items-center gap-1.5 px-2 py-1 rounded min-h-[26px] transition-colors ${
          isActive
            ? 'bg-accent-soft text-accent font-medium'
            : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
        } ${dropClass}`}
        style={{ paddingLeft: depth * 12 + 8 }}
        title={dropTitle}
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
          <span aria-hidden className="mr-1">📁</span>{name}
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
      {isExpanded && children.isError && (
        <div role="alert" className="px-2 py-0.5 text-[11px] text-danger" style={{ paddingLeft: (depth + 1) * 12 + 8 }}>
          로드 실패
        </div>
      )}
    </div>
  )
}
