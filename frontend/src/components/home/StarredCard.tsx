'use client'
import Link from 'next/link'
import { DashboardCard } from './DashboardCard'
import { useMyFavorites } from '@/hooks/useMyFavorites'
import type { FavoriteItem } from '@/types/favorite'

/**
 * User Home Dashboard ② — 즐겨찾기 8개.
 *
 * <p>favorites-list 트랙 (PR #243) 의 `useMyFavorites` 결과를 reuse — 인자 없음 (server 가 전체 list
 * 반환). 본 카드는 8건만 slice. 전체 보기는 `/favorites` 페이지 (PR #243).
 *
 * <p>row 표시 축소: name + type chip + starredAt date. workspace 경로/수정일은 follow-up 트랙
 * (FavoriteItem 필드 보강 후).
 */
export function StarredCard() {
  const { data, isLoading, isError } = useMyFavorites()
  const items: FavoriteItem[] = (data?.items ?? []).slice(0, 8)

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
          {items.map((it) => (
            <li
              key={`${it.resourceType}:${it.resourceId}`}
              className="flex items-center justify-between py-1 text-[13px]"
            >
              <span className="truncate text-fg">{it.name}</span>
              <span className="text-[11px] px-1.5 py-0.5 rounded bg-bg-2 text-fg-2 shrink-0 ml-2">
                {it.resourceType === 'folder' ? '폴더' : '파일'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </DashboardCard>
  )
}
