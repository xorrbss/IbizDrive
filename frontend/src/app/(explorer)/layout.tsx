import { FolderTree } from '@/components/folders/FolderTree'

export default function ExplorerLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen">
      <aside className="w-64 border-r p-4 overflow-y-auto">
        <FolderTree />
      </aside>
      <main className="flex-1 p-6 overflow-y-auto">{children}</main>
    </div>
  )
}
