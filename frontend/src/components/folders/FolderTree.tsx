'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useFolderTree } from '@/hooks/useFolderTree'
import { useViewStore } from '@/stores/view'
import { buildCanonicalPath } from '@/lib/folderPath'
import type { FolderNode as FolderNodeType } from '@/types/folder'

export function FolderTree() {
  const { data: tree, isLoading } = useFolderTree()
  const { folderId: activeId } = useCurrentFolder()

  if (isLoading) return <FolderTreeSkeleton />
  if (!tree) return null

  return (
    <nav aria-label="폴더 트리">
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
  const isExpanded = expandedFolderIds.includes(node.id)
  const isActive = activeId === node.id
  const nextPath = node.id === 'root' ? [] : [...pathAcc, node.slug]
  const href = buildCanonicalPath(node.id, nextPath)

  return (
    <div>
      <div
        className={`flex items-center gap-1 px-2 py-1 rounded hover:bg-gray-100 ${
          isActive ? 'bg-blue-100 text-blue-900' : ''
        }`}
        style={{ paddingLeft: depth * 12 + 8 }}
      >
        {node.children?.length ? (
          <button
            onClick={() => toggleExpanded(node.id)}
            aria-label={isExpanded ? '접기' : '펼치기'}
            aria-expanded={isExpanded}
            className="w-4 text-center"
          >
            {isExpanded ? '▾' : '▸'}
          </button>
        ) : (
          <span className="w-4" />
        )}
        <Link href={href} className="flex-1 truncate">
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
    <div className="space-y-2 animate-pulse">
      {[1, 2, 3].map((i) => (
        <div key={i} className="h-6 bg-gray-200 rounded" />
      ))}
    </div>
  )
}
