'use client'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { DashboardCard } from './DashboardCard'
import { useMyFavorites } from '@/hooks/useMyFavorites'
import type { FavoriteItem } from '@/types/favorite'
import { buildWorkspacePath } from '@/lib/workspacePath'

/**
 * User Home Dashboard ② — 즐겨찾기 8개.
 *
 * <p>favorites-list 트랙 (PR #243) 의 `useMyFavorites` 결과를 reuse — 인자 없음 (server 가 전체 list
 * 반환). 본 카드는 8건만 slice. 전체 보기는 `/favorites` 페이지 (PR #243).
 *
 * <p>row 클릭 시 (follow-up 2026-05-14):
 * <ul>
 *   <li>file → `buildWorkspacePath(scope, parentId) + ?file=resourceId` (RightPanel 자동 오픈)</li>
 *   <li>folder → `buildWorkspacePath(scope, resourceId)` (folder 자체 진입 — root 면 parentId=null)</li>
 * </ul>
 * scope 가 없는 row 는 navigation skip (button disabled). FavoriteItem.scope 는 backend
 * `@JsonInclude(NON_NULL)` 로 omit 가능하지만 정상 row 는 항상 보유.
 */
export function StarredCard() {
  const router = useRouter()
  const { data, isLoading, isError } = useMyFavorites()
  const items: FavoriteItem[] = (data?.items ?? []).slice(0, 8)

  function navigateTo(it: FavoriteItem) {
    if (!it.scope) return
    const folderIdToNav = it.resourceType === 'folder' ? it.resourceId : it.parentId
    if (!folderIdToNav) return
    const url = buildWorkspacePath(
      { kind: it.scope.type, workspaceId: it.scope.id },
      folderIdToNav,
      [],
    )
    router.push(it.resourceType === 'file' ? `${url}?file=${it.resourceId}` : url)
  }

  return (
    <DashboardCard
      title="즐겨찾기"
      subtitle="별표 표시한 항목"
      right={
        <Link href="/favorites" className="text-[12px] text-fg-2 hover:text-fg">
          전체 보기 →
        </Link>
      }
    >
      {isLoading && <div className="text-[13px] text-fg-muted">불러오는 중…</div>}
      {isError && (
        <div className="text-[13px] text-fg-muted">즐겨찾기를 불러올 수 없습니다.</div>
      )}
      {!isLoading && !isError && items.length === 0 && (
        <div className="text-[13px] text-fg-muted">
          아직 즐겨찾기한 항목이 없습니다. 파일 이름 옆 ☆ 아이콘을 눌러 추가하세요.
        </div>
      )}
      {items.length > 0 && (
        <ul role="list" className="space-y-1">
          {items.map((it) => {
            const navigable = !!it.scope && (it.resourceType === 'folder' || !!it.parentId)
            return (
              <li key={`${it.resourceType}:${it.resourceId}`} className="text-[13px]">
                <button
                  type="button"
                  onClick={() => navigateTo(it)}
                  disabled={!navigable}
                  className="flex w-full items-center justify-between gap-2 py-1 px-1 -mx-1 rounded hover:bg-surface-2 text-left disabled:cursor-default disabled:hover:bg-transparent"
                  aria-label={`${it.name} 열기`}
                >
                  <span className="truncate text-fg">{it.name}</span>
                  <span className="text-[11px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-2 shrink-0 ml-2">
                    {it.resourceType === 'folder' ? '폴더' : '파일'}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </DashboardCard>
  )
}
