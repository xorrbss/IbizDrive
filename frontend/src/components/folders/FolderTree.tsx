'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useFolderTree } from '@/hooks/useFolderTree'
import { useViewStore } from '@/stores/view'
import { buildCanonicalPath } from '@/lib/folderPath'
import { useFolderDroppable } from '@/components/dnd/useFolderDroppable'
import type { FolderNode as FolderNodeType } from '@/types/folder'

export function FolderTree() {
  const { data: tree, isLoading } = useFolderTree()
  const { folderId: activeId } = useCurrentFolder()

  if (isLoading) return <FolderTreeSkeleton />
  if (!tree) return null

  return (
    <nav aria-label="폴더 트리" className="text-[12.5px]">
      <FolderNodeItem node={tree} activeId={activeId} depth={0} pathAcc={[]} />
    </nav>
  )
}

function FolderNodeItem({
  node,
  activeId,
  depth,
  pathAcc,
}: {
  node: FolderNodeType
  activeId: string
  depth: number
  pathAcc: string[]
}) {
  const { expandedFolderIds, toggleExpanded } = useViewStore()
  const { setNodeRef, isOver, isInvalid, isDragging, isSameFolder } =
    useFolderDroppable(node.id)
  const isExpanded = expandedFolderIds.includes(node.id)
  const isActive = activeId === node.id
  const nextPath = node.id === 'root' ? [] : [...pathAcc, node.slug]
  const href = buildCanonicalPath(node.id, nextPath)

  // 드래그 중일 때만 droppable 시각화 적용
  const dragClass = !isDragging
    ? ''
    : isInvalid || isSameFolder
      ? 'opacity-50'
      : isOver
        ? 'bg-accent-soft ring-2 ring-accent'
        : ''

  return (
    <div>
      <div
        ref={setNodeRef}
        aria-dropeffect={
          isDragging && !isInvalid && !isSameFolder ? 'move' : undefined
        }
        className={`flex items-center gap-1.5 px-2 py-1 rounded min-h-[26px] transition-colors ${
          isActive
            ? 'bg-accent-soft text-accent font-medium'
            : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
        } ${dragClass}`}
        style={{ paddingLeft: depth * 12 + 8 }}
      >
        {node.children?.length ? (
          <button
            onClick={() => toggleExpanded(node.id)}
            aria-label={isExpanded ? '접기' : '펼치기'}
            aria-expanded={isExpanded}
            className="w-3.5 inline-flex items-center justify-center text-fg-muted text-[10px]"
          >
            {isExpanded ? '▾' : '▸'}
          </button>
        ) : (
          <span className="w-3.5" />
        )}
        <Link href={href} className="flex-1 truncate text-inherit">
          📁 {node.name}
        </Link>
      </div>
      {isExpanded &&
        node.children?.map((child) => (
          <FolderNodeItem
            key={child.id}
            node={child}
            activeId={activeId}
            depth={depth + 1}
            pathAcc={nextPath}
          />
        ))}
    </div>
  )
}

function FolderTreeSkeleton() {
  return (
    <div className="space-y-1.5 animate-pulse" aria-hidden>
      {[1, 2, 3].map((i) => (
        <div key={i} className="h-6 bg-surface-2 rounded" />
      ))}
    </div>
  )
}
