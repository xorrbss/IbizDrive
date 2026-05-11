'use client'
import { Menu } from 'lucide-react'
import { TweaksPanel } from './TweaksPanel'
import { SearchBar } from './SearchBar'
import { Avatar } from './Avatar'
import { useSidebarChromeStore } from '@/stores/sidebarChrome'

/**
 * 탐색기 상단 바 — 디자인 핸드오프 G2 (3-column grid, 2026-05-11).
 *
 * <p>레이아웃: `grid auto / 1fr / auto` (디자인 styles.css L134).
 * - 좌측: 햄버거 버튼 (사이드바 collapse 토글, {@link useSidebarChromeStore})
 * - 중앙: SearchBar (`max-w-[560px] mx-auto`)
 * - 우측: TweaksPanel + Avatar
 *
 * <p>햄버거는 collapsed 상태에서도 노출되어야 사이드바를 다시 열 수 있다. `aria-pressed`로
 * 토글 상태를 SR에 노출.
 *
 * <p>Avatar는 M14 placeholder — 백엔드 `/api/me` 신설 후 useMe() 훅으로 교체.
 */
export function TopBar() {
  const collapsed = useSidebarChromeStore((s) => s.collapsed)
  const toggle = useSidebarChromeStore((s) => s.toggle)

  return (
    <div
      role="banner"
      className="grid grid-cols-[auto_1fr_auto] items-center gap-2 h-12 px-3 border-b border-border bg-surface-1"
    >
      <button
        type="button"
        onClick={toggle}
        aria-pressed={collapsed}
        aria-label="사이드바 접기/펴기"
        className="h-8 w-8 flex items-center justify-center rounded-md text-fg-muted hover:bg-surface-2 hover:text-fg focus:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      >
        <Menu size={16} aria-hidden />
      </button>
      <div className="mx-auto w-full max-w-[560px]">
        <SearchBar />
      </div>
      <div className="flex items-center gap-2">
        <TweaksPanel />
        <Avatar />
      </div>
    </div>
  )
}
