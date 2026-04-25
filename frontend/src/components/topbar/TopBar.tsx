'use client'
import { ThemeToggle } from './ThemeToggle'

/**
 * 탐색기 상단 바 — 좌측은 비워 두고(향후 검색/네비) 우측에 테마 토글 등 글로벌 액션 배치.
 *
 * docs/01 §17 라우팅 구조 영향 없음. layout.tsx에서 main 상단에 고정 배치.
 */
export function TopBar() {
  return (
    <div
      role="banner"
      className="flex items-center justify-end gap-1 h-10 px-3 border-b border-border bg-surface-1"
    >
      <ThemeToggle />
    </div>
  )
}
