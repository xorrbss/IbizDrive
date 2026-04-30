import { FolderTree } from '@/components/folders/FolderTree'
import { DndProvider } from '@/components/dnd/DndProvider'
import { TopBar } from '@/components/topbar/TopBar'
import { TrashLink } from '@/components/trash/TrashLink'
import { StatusBar } from '@/components/statusbar/StatusBar'

export default function ExplorerLayout({ children }: { children: React.ReactNode }) {
  return (
    <DndProvider>
      <div className="flex h-screen w-screen bg-bg text-fg overflow-hidden">
        <aside
          aria-label="사이드바"
          className="w-[248px] shrink-0 bg-surface-1 border-r border-border flex flex-col gap-1 overflow-y-auto p-2.5"
        >
          <div className="flex items-center gap-2 px-2 pt-1 pb-3">
            <span
              aria-hidden
              className="w-[22px] h-[22px] rounded-sm bg-accent inline-block"
            />
            <span className="text-[14px] font-semibold tracking-tight text-fg">IbizDrive</span>
          </div>
          <FolderTree />
          <div className="mt-auto pt-2 border-t border-border">
            <TrashLink />
          </div>
        </aside>
        <main className="flex-1 min-w-0 flex flex-col bg-bg overflow-hidden">
          <TopBar />
          <div className="flex-1 min-h-0 flex flex-col">{children}</div>
          <StatusBar />
        </main>
      </div>
    </DndProvider>
  )
}
