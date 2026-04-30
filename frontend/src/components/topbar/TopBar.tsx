'use client'
import { ThemeToggle } from './ThemeToggle'
import { SearchBar } from './SearchBar'
import { Avatar } from './Avatar'

/**
 * 탐색기 상단 바 — 좌측에 검색(M11), 우측에 글로벌 액션(테마 토글 + 아바타).
 *
 * docs/01 §17 라우팅 구조 영향 없음. layout.tsx에서 main 상단에 고정 배치.
 * Avatar는 M14 placeholder — 백엔드 `/api/me` 신설 후 useMe() 훅으로 교체.
 */
export function TopBar() {
  return (
    <div
      role="banner"
      className="flex items-center justify-between gap-2 h-10 px-3 border-b border-border bg-surface-1"
    >
      <SearchBar />
      <div className="flex items-center gap-2">
        <ThemeToggle />
        <Avatar />
      </div>
    </div>
  )
}
