import { create } from 'zustand'
import {
  DEFAULT_FILE_FILTERS,
  type FileFilters,
  type FileKindId,
  type FileModifiedId,
} from '@/types/fileFilters'

/**
 * 파일/폴더 목록 필터 상태 — design-zip §FilterPopover/§FilterChips. folder navigation 와 무관하게
 * 사용자 세션 동안 유지 (v1.x 결정: folder 별 reset 안 함, 같은 의도가 다음 폴더에서도 적용되는 경우가
 * 자주 있으므로). reset 은 명시적 사용자 액션으로만.
 */
interface FileFiltersStore {
  filters: FileFilters
  setFilters: (next: FileFilters) => void
  toggleKind: (id: FileKindId) => void
  setModified: (m: FileModifiedId) => void
  setStarred: (v: boolean) => void
  setShared: (v: boolean) => void
  reset: () => void
}

export const useFileFiltersStore = create<FileFiltersStore>((set) => ({
  filters: DEFAULT_FILE_FILTERS,
  setFilters: (next) => set({ filters: next }),
  toggleKind: (id) =>
    set((s) => {
      const has = s.filters.kinds.includes(id)
      return {
        filters: {
          ...s.filters,
          kinds: has
            ? s.filters.kinds.filter((k) => k !== id)
            : [...s.filters.kinds, id],
        },
      }
    }),
  setModified: (m) => set((s) => ({ filters: { ...s.filters, modified: m } })),
  setStarred: (v) => set((s) => ({ filters: { ...s.filters, starred: v } })),
  setShared: (v) => set((s) => ({ filters: { ...s.filters, shared: v } })),
  reset: () => set({ filters: DEFAULT_FILE_FILTERS }),
}))
