'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { usePermission } from '@/hooks/usePermission'
import { useShareUiStore } from '@/stores/shareUi'
import { buildWorkspacePath, type WorkspaceLocator } from '@/lib/workspacePath'
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { useFolderDroppable } from '@/components/dnd/useFolderDroppable'

interface HeadCrumb {
  id: string
  name: string
  href: string
}

function useWorkspaceHeadCrumb(): HeadCrumb | null {
  const ws = useCurrentWorkspace()
  const { data } = useWorkspaces()
  if (!ws) return null
  if (ws.section === 'department' && data?.department?.id === ws.workspaceId) {
    return {
      id: data.department.rootFolderId,
      name: data.department.name,
      href: buildWorkspacePath({ kind: 'department', workspaceId: ws.workspaceId! }, data.department.rootFolderId, []),
    }
  }
  if (ws.section === 'team') {
    const t = data?.teams.find((x) => x.id === ws.workspaceId)
    if (t) return { id: t.rootFolderId, name: t.name, href: buildWorkspacePath({ kind: 'team', workspaceId: ws.workspaceId! }, t.rootFolderId, []) }
  }
  if (ws.section === 'shared') {
    return { id: 'shared', name: '공유받음', href: buildWorkspacePath({ kind: 'shared' }, null, []) }
  }
  return null
}

export function Breadcrumb() {
  const { folderId, breadcrumb, isLoading } = useCurrentFolder()
  // F5.3: 폴더 공유 진입점 — 현재 폴더 권한 기준으로 SHARE 가능할 때만 노출.
  // 현재 폴더 = breadcrumb 마지막 항목 (= folderId 동일). 루트 외 폴더에서 동작.
  const can = usePermission(folderId)
  const openShare = useShareUiStore((s) => s.open)
  const ws = useCurrentWorkspace()
  const headCrumb = useWorkspaceHeadCrumb()
  if (isLoading) return <BreadcrumbSkeleton />

  const current = breadcrumb[breadcrumb.length - 1]
  // 루트(=breadcrumb 1개 — "내 드라이브"만 있는 경우)는 share 진입점 비노출.
  // backend는 root 공유를 허용하지 않는 정책으로 가정 (루트는 시스템 폴더). UI 차단으로 충분.
  const showShare = !!current && breadcrumb.length > 1 && can.SHARE

  const loc: WorkspaceLocator | null = ws
    ? ws.section === 'shared'
      ? { kind: 'shared' }
      : { kind: ws.section as 'department' | 'team', workspaceId: ws.workspaceId! }
    : null

  // spec §4.5 §2: workspace root는 트리 1차 노드, 이름 = workspace 이름.
  // headCrumb이 있으면 breadcrumb의 첫 가상 root('내 드라이브')를 workspace 이름으로 교체.
  // headCrumb이 null이면 (비workspace 라우트) 기존 동작 유지.
  const displayCrumbs = headCrumb
    ? [
        { id: headCrumb.id, name: headCrumb.name, slugPath: [] as string[], _href: headCrumb.href },
        ...breadcrumb.slice(1).map((c) => ({
          ...c,
          _href: loc ? buildWorkspacePath(loc, c.id, c.slugPath) : '#',
        })),
      ]
    : breadcrumb.map((c) => ({
        ...c,
        _href: loc ? buildWorkspacePath(loc, c.id, c.slugPath) : '#',
      }))

  return (
    <nav
      aria-label="Breadcrumb"
      className="flex items-center gap-0.5 flex-wrap px-4 pt-2.5 pb-1.5 text-[13.5px] text-fg-muted"
    >
      {displayCrumbs.map((c, i) => {
        const last = i === displayCrumbs.length - 1
        return (
          <span key={c.id} className="inline-flex items-center">
            {i > 0 && (
              <span className="text-fg-subtle px-0.5" aria-hidden>
                ›
              </span>
            )}
            {last ? (
              <span className="px-1.5 py-[3px] text-[15px] font-semibold text-fg">
                {c.name}
              </span>
            ) : (
              <BreadcrumbLink id={c.id} href={c._href} name={c.name} />
            )}
          </span>
        )
      })}
      {showShare && current && (
        <button
          type="button"
          onClick={() => openShare({ kind: 'folder', id: current.id, name: current.name })}
          className="ml-2 h-7 px-2.5 inline-flex items-center gap-1 rounded text-[12.5px] text-fg-2 hover:bg-surface-2 hover:text-fg transition-colors"
          aria-label={`${current.name} 폴더 공유`}
        >
          공유
        </button>
      )}
    </nav>
  )
}

function BreadcrumbLink({ id, href, name }: { id: string; href: string; name: string }) {
  const { setNodeRef, isOver, isInvalid, isDragging, isSameFolder } =
    useFolderDroppable(id)
  const dragClass = !isDragging
    ? ''
    : isInvalid || isSameFolder
      ? 'opacity-50'
      : isOver
        ? 'bg-accent-soft ring-2 ring-accent text-accent'
        : ''
  return (
    <Link
      ref={setNodeRef as React.Ref<HTMLAnchorElement>}
      href={href}
      aria-dropeffect={
        isDragging && !isInvalid && !isSameFolder ? 'move' : undefined
      }
      className={`px-1.5 py-[3px] rounded-sm text-fg-muted hover:bg-surface-2 hover:text-fg transition-colors ${dragClass}`}
    >
      {name}
    </Link>
  )
}

function BreadcrumbSkeleton() {
  return (
    <div
      className="flex gap-2 px-4 pt-2.5 pb-1.5 animate-pulse"
      aria-hidden
    >
      <div className="h-4 w-16 bg-surface-2 rounded" />
      <div className="h-4 w-24 bg-surface-2 rounded" />
    </div>
  )
}
