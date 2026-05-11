'use client'
import { Suspense } from 'react'
import { SidebarSections } from '@/components/sidebar/SidebarSections'
import { SidebarNewButton } from '@/components/sidebar/SidebarNewButton'
import { TrashLink } from '@/components/trash/TrashLink'
import { SharesLink } from '@/components/shares/SharesLink'
import { DndProvider } from '@/components/dnd/DndProvider'
import { TopBar } from '@/components/topbar/TopBar'
import { StatusBar } from '@/components/statusbar/StatusBar'
import { StorageBar } from '@/components/storage/StorageBar'
import { AuthGuard } from '@/components/auth/AuthGuard'
import { UserMenu } from '@/components/auth/UserMenu'
import { ShortcutsCheatSheet } from '@/components/topbar/ShortcutsCheatSheet'
import { useSidebarChromeStore } from '@/stores/sidebarChrome'

export default function ExplorerLayout({ children }: { children: React.ReactNode }) {
  // 사이드바 collapse — chrome 토글. 폭은 transition으로 부드럽게 전환, 내부 콘텐츠는
  // overflow-hidden로 잘림 처리 + aria-hidden로 SR이 collapsed 상태에서 트리를 읽지 않게 함.
  const collapsed = useSidebarChromeStore((s) => s.collapsed)

  return (
    <AuthGuard>
      <DndProvider>
        <div className="flex h-screen w-screen bg-bg text-fg overflow-hidden">
          <aside
            aria-label="사이드바"
            aria-hidden={collapsed}
            className={`shrink-0 bg-surface-1 border-r border-border flex flex-col gap-1 overflow-hidden transition-[width] duration-200 ease-out ${
              collapsed ? 'w-0 border-r-0' : 'w-[248px]'
            }`}
          >
            <div className="w-[248px] flex flex-col gap-1 overflow-y-auto p-2.5 flex-1">
              <div className="flex items-center gap-2 px-2 pt-1 pb-3">
                <span
                  aria-hidden
                  className="w-[22px] h-[22px] rounded-sm bg-accent inline-block"
                />
                <span className="text-[14px] font-semibold tracking-tight text-fg">IbizDrive</span>
              </div>
              {/* design-sweep-phase-2b: zip components.jsx Sidebar primary "새로 만들기" 진입점.
                  brand 마크와 nav/folder tree 사이에 배치(원본 components.jsx L24 NewButton 위치). */}
              <SidebarNewButton />
              <SidebarSections />
              <div className="mt-auto pt-2 border-t border-border">
                <SharesLink />
                <TrashLink />
                <StorageBar />
                <UserMenu />
              </div>
            </div>
          </aside>
          <main className="flex-1 min-w-0 flex flex-col bg-bg overflow-hidden">
            <TopBar />
            <div className="flex-1 min-h-0 flex flex-col">{children}</div>
            {/* Next.js 15: useSearchParams() 사용 컴포넌트는 Suspense boundary 필요.
                StatusBar가 useSortParams → useSearchParams를 호출하므로,
                해당 boundary 없이는 /trash 등 SSG 경로의 prerender가 실패함. */}
            <Suspense fallback={null}>
              <StatusBar />
            </Suspense>
          </main>
        </div>
        {/* `?` 단축키 cheat sheet — self-managed visibility, 단 1회 마운트로 충분. */}
        <ShortcutsCheatSheet />
      </DndProvider>
    </AuthGuard>
  )
}
