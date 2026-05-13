'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Star } from 'lucide-react'
import { useMyFavorites } from '@/hooks/useMyFavorites'

/**
 * 사이드바 최상단 pinned row — 현재는 "즐겨찾기" 1개만.
 *
 * <p>spec §4.5 team-centric pivot 사이드바(부서/팀/공유받음 3 section) 위에 위치.
 * zip 디자인 line 31 `SidebarLink icon=star label=즐겨찾기 count={3}` 대응.
 * 휴지통 패턴 답습 — entry는 사이드바, 콘텐츠는 별도 페이지(`/favorites`).
 *
 * <p>count badge = 활성 favorites 수. backend가 soft-deleted resource를 자연 제외하므로
 * 화면 가능한 count와 일치. 401(미인증) 또는 isLoading 시 count 비표시.
 */
export function SidebarPinnedRow() {
  const pathname = usePathname()
  const { data } = useMyFavorites()
  const count = data?.items.length ?? null
  const isActive = pathname?.startsWith('/favorites')

  return (
    <Link
      href="/favorites"
      aria-current={isActive ? 'page' : undefined}
      className={`group flex items-center gap-2 px-2 py-1.5 rounded text-[12.5px] transition-colors ${
        isActive
          ? 'bg-accent-soft text-fg font-medium'
          : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
      }`}
    >
      <Star
        size={14}
        aria-hidden
        className={`flex-shrink-0 ${isActive ? 'text-warn fill-warn' : 'text-fg-muted'}`}
      />
      <span className="flex-1 truncate">즐겨찾기</span>
      {count != null && count > 0 && (
        <span
          className="text-[10.5px] tabular-nums text-fg-muted px-1.5 py-0 border border-border rounded-full"
          aria-label={`즐겨찾기 ${count}개`}
        >
          {count}
        </span>
      )}
    </Link>
  )
}
