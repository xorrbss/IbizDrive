import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { SidebarSectionKind } from '@/lib/workspacePath'

interface SidebarTreeState {
  /** 펼쳐진 폴더 ID 집합 — workspace 전역 (folder UUID는 항상 unique). */
  expandedFolderIds: string[]
  /** 접힌 section 목록 — 'department' | 'team' | 'shared'. 기본 모두 펼침. */
  collapsedSections: SidebarSectionKind[]

  toggleFolder: (id: string) => void
  expandFolder: (id: string) => void
  collapseFolder: (id: string) => void
  toggleSection: (kind: SidebarSectionKind) => void
}

export const useSidebarTreeStore = create<SidebarTreeState>()(
  persist(
    (set) => ({
      expandedFolderIds: [],
      collapsedSections: [],

      toggleFolder: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.includes(id)
            ? s.expandedFolderIds.filter((x) => x !== id)
            : [...s.expandedFolderIds, id],
        })),
      expandFolder: (id) =>
        set((s) =>
          s.expandedFolderIds.includes(id)
            ? s
            : { ...s, expandedFolderIds: [...s.expandedFolderIds, id] },
        ),
      collapseFolder: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.filter((x) => x !== id),
        })),
      toggleSection: (kind) =>
        set((s) => ({
          collapsedSections: s.collapsedSections.includes(kind)
            ? s.collapsedSections.filter((x) => x !== kind)
            : [...s.collapsedSections, kind],
        })),
    }),
    {
      name: 'sidebar-tree-state:v1',
      // SSR/hydration mismatch 방지 — Next.js 15 App Router 디폴트 storage(localStorage)
    },
  ),
)
