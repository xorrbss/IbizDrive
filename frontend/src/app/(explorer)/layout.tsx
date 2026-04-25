import { Toaster } from 'sonner'
import { FolderTree } from '@/components/folders/FolderTree'
import { DndProvider } from '@/components/dnd/DndProvider'
import { TopBar } from '@/components/layout/TopBar'
import { StorageBar } from '@/components/layout/StorageBar'
import { TrashLink } from '@/components/layout/TrashLink'

export default function ExplorerLayout({ children }: { children: React.ReactNode }) {
  return (
    <DndProvider>
      <div className="grid grid-rows-[48px_1fr] h-screen w-screen bg-bg text-fg overflow-hidden">
        <TopBar />
        <div className="grid grid-cols-[248px_1fr] min-h-0">
          <aside
            aria-label="사이드바"
            className="bg-surface-1 border-r border-border flex flex-col gap-1 overflow-y-auto p-2.5"
          >
            <div className="flex items-center gap-2 px-2 pt-1 pb-3">
              <span
                aria-hidden
                className="w-[22px] h-[22px] rounded-sm bg-accent inline-block"
              />
              <span className="text-[14px] font-semibold tracking-tight text-fg">IbizDrive</span>
            </div>
            <FolderTree />
            <TrashLink />
            <StorageBar />
          </aside>
          <main className="min-w-0 flex flex-col bg-bg overflow-hidden">
            {children}
          </main>
        </div>
      </div>
      <Toaster position="bottom-right" richColors closeButton />
    </DndProvider>
  )
}
