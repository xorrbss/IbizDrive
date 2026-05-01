'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { usePermission } from '@/hooks/usePermission'
import { useShareUiStore } from '@/stores/shareUi'
import { buildCanonicalPath } from '@/lib/folderPath'
import { useFolderDroppable } from '@/components/dnd/useFolderDroppable'

export function Breadcrumb() {
  const { folderId, breadcrumb, isLoading } = useCurrentFolder()
  // F5.3: 폴더 공유 진입점 — 현재 폴더 권한 기준으로 SHARE 가능할 때만 노출.
  // 현재 폴더 = breadcrumb 마지막 항목 (= folderId 동일). 루트 외 폴더에서 동작.
  const can = usePermission(folderId)
  const openShare = useShareUiStore((s) => s.open)
  if (isLoading) return <BreadcrumbSkeleton />

  const current = breadcrumb[breadcrumb.length - 1]
  // 루트(=breadcrumb 1개 — "내 드라이브"만 있는 경우)는 share 진입점 비노출.
  // backend는 root 공유를 허용하지 않는 정책으로 가정 (루트는 시스템 폴더). UI 차단으로 충분.
  const showShare = !!current && breadcrumb.length > 1 && can.SHARE

  return (
    <nav
      aria-label="Breadcrumb"
      className="flex items-center gap-0.5 flex-wrap px-4 pt-2.5 pb-1.5 text-[13.5px] text-fg-muted"
    >
      {breadcrumb.map((c, i) => {
        const href = buildCanonicalPath(c.id, c.slugPath)
        const last = i === breadcrumb.length - 1
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
              <BreadcrumbLink id={c.id} href={href} name={c.name} />
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
