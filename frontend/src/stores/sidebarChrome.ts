import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * 사이드바 외곽(chrome) 토글 상태 — 디자인 핸드오프 G2/3 트랙(2026-05-11).
 *
 * <p>{@link useSidebarTreeStore}와 책임 분리:
 * - sidebarTree → 폴더 expand state + section collapsed (TTL 30일 마이그레이트)
 * - sidebarChrome → 사이드바 자체 collapse 토글 (단일 boolean)
 *
 * <p>persist 키 `sidebar-chrome:v1`. SSR hydration 후 client에서 collapsed 적용 — 첫 paint는
 * default(false)이므로 사이드바가 잠시 열렸다 닫히는 깜빡임이 발생할 수 있으나 KISS 수용.
 */
interface SidebarChromeState {
  collapsed: boolean
  setCollapsed: (next: boolean) => void
  toggle: () => void
}

export const useSidebarChromeStore = create<SidebarChromeState>()(
  persist(
    (set) => ({
      collapsed: false,
      setCollapsed: (next) => set({ collapsed: next }),
      toggle: () => set((s) => ({ collapsed: !s.collapsed })),
    }),
    { name: 'sidebar-chrome:v1' },
  ),
)
