import { create } from 'zustand'
import { persist } from 'zustand/middleware'

type ViewState = {
  expandedFolderIds: string[]
  toggleExpanded: (id: string) => void
}

export const useViewStore = create<ViewState>()(
  persist(
    (set) => ({
      expandedFolderIds: ['root'],
      toggleExpanded: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.includes(id)
            ? s.expandedFolderIds.filter((x) => x !== id)
            : [...s.expandedFolderIds, id],
        })),
    }),
    { name: 'explorer-view' }
  )
)
