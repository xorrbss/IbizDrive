'use client'
import Link from 'next/link'
import { Star, FileText, Folder as FolderIcon } from 'lucide-react'
import { useMyFavorites } from '@/hooks/useMyFavorites'
import { buildWorkspacePath, type WorkspaceLocator } from '@/lib/workspacePath'
import type { FavoriteItem } from '@/types/favorite'

/**
 * `/favorites` — 즐겨찾기 listing 페이지.
 *
 * <p>spec §4.5 pinned shortcuts entry. file/folder 혼재, 최신순. 클릭 시 해당 resource의
 * workspace 경로로 라우팅 (`/d/:id/...` 또는 `/t/:id/...`).
 *
 * <p>shared workspace의 favorites는 scope 미주입 가능성이 있어 분기 처리:
 * scope 없으면 link disabled + 경고 시각.
 */
export default function FavoritesPage() {
  const { data, isLoading, error } = useMyFavorites()

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        로딩…
      </div>
    )
  }
  if (error) {
    return (
      <div role="alert" className="flex-1 flex items-center justify-center text-[13px] text-danger">
        에러: {String(error)}
      </div>
    )
  }
  const items = data?.items ?? []

  return (
    <div className="flex-1 min-w-0 flex flex-col bg-bg">
      <header className="px-4 pt-4 pb-2 border-b border-border">
        <h1 className="text-[16px] font-semibold text-fg inline-flex items-center gap-2">
          <Star size={16} aria-hidden className="text-warn fill-warn" />
          즐겨찾기
          <span
            className="text-[11px] tabular-nums text-fg-muted ml-1"
            aria-label={`${items.length}개 항목`}
          >
            ({items.length})
          </span>
        </h1>
      </header>
      {items.length === 0 ? (
        <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
          즐겨찾기로 추가한 파일/폴더가 여기에 모입니다.
        </div>
      ) : (
        <ul className="flex-1 overflow-auto px-4 py-2 space-y-0.5" role="list">
          {items.map((item) => (
            <FavoriteRow key={`${item.resourceType}:${item.resourceId}`} item={item} />
          ))}
        </ul>
      )}
    </div>
  )
}

function FavoriteRow({ item }: { item: FavoriteItem }) {
  const href = resolveHref(item)
  const Icon = item.resourceType === 'folder' ? FolderIcon : FileText
  const subtitle =
    item.resourceType === 'folder' ? '폴더' : '파일'

  if (!href) {
    return (
      <li
        className="flex items-center gap-2 px-2 py-1.5 rounded text-[12.5px] text-fg-subtle"
        aria-disabled="true"
        title="이 항목의 workspace 정보를 찾을 수 없습니다 (scope 누락)"
      >
        <Icon size={14} aria-hidden className="flex-shrink-0" />
        <span className="flex-1 truncate">{item.name}</span>
        <span className="text-[11px] text-fg-subtle">{subtitle}</span>
      </li>
    )
  }

  return (
    <li>
      <Link
        href={href}
        className="flex items-center gap-2 px-2 py-1.5 rounded text-[12.5px] text-fg-2 hover:bg-surface-2 hover:text-fg transition-colors"
      >
        <Icon size={14} aria-hidden className="flex-shrink-0" />
        <span className="flex-1 truncate">{item.name}</span>
        <span className="text-[11px] text-fg-subtle">{subtitle}</span>
      </Link>
    </li>
  )
}

/**
 * scope + resourceType + parentId로 workspace 경로 합성.
 * file: parent folder로 이동 + `?file=resourceId` (RightPanel 자동 open — §17.2 패턴).
 * folder: 자기 folderId로 이동 (canonical redirect가 slug 보정).
 */
function resolveHref(item: FavoriteItem): string | null {
  if (!item.scope) return null
  const loc: WorkspaceLocator =
    item.scope.type === 'team'
      ? { kind: 'team', workspaceId: item.scope.id }
      : { kind: 'department', workspaceId: item.scope.id }

  if (item.resourceType === 'folder') {
    return buildWorkspacePath(loc, item.resourceId, [])
  }
  // file: parentId 없으면 workspace root로 fallback. RightPanel은 ?file= query로 open.
  const folderForFile = item.parentId
  if (!folderForFile) return null
  return `${buildWorkspacePath(loc, folderForFile, [])}?file=${encodeURIComponent(item.resourceId)}`
}
