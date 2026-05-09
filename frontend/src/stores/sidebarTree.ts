import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { SidebarSectionKind } from '@/lib/workspacePath'

const THIRTY_DAYS = 30 * 24 * 3600 * 1000

interface SidebarTreeState {
  /** 펼쳐진 폴더 ID 집합 — workspace 전역 (folder UUID는 항상 unique). */
  expandedFolderIds: string[]
  /** 접힌 section 목록 — 'department' | 'team' | 'shared'. 기본 모두 펼침. */
  collapsedSections: SidebarSectionKind[]
  /** 마지막 상태 변경 타임스탬프 (ms). 30일 초과 시 persist migrate에서 reset. */
  lastWriteAt: number

  toggleFolder: (id: string) => void
  expandFolder: (id: string) => void
  collapseFolder: (id: string) => void
  toggleSection: (kind: SidebarSectionKind) => void
}

/** 30일 초과 stale 상태를 reset하는 persist migrate 함수. 테스트에서 직접 호출 가능. */
export function migrateSidebarTree(persisted: unknown): unknown {
  if (!persisted || typeof persisted !== 'object') return persisted
  const state = persisted as Record<string, unknown>
  if (
    typeof state.lastWriteAt === 'number' &&
    Date.now() - state.lastWriteAt > THIRTY_DAYS
  ) {
    return { expandedFolderIds: [], collapsedSections: [], lastWriteAt: Date.now() }
  }
  return persisted
}

export const useSidebarTreeStore = create<SidebarTreeState>()(
  persist(
    (set) => ({
      expandedFolderIds: [],
      collapsedSections: [],
      lastWriteAt: Date.now(),

      toggleFolder: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.includes(id)
            ? s.expandedFolderIds.filter((x) => x !== id)
            : [...s.expandedFolderIds, id],
          lastWriteAt: Date.now(),
        })),
      expandFolder: (id) =>
        set((s) =>
          s.expandedFolderIds.includes(id)
            ? s
            : { ...s, expandedFolderIds: [...s.expandedFolderIds, id], lastWriteAt: Date.now() },
        ),
      collapseFolder: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.filter((x) => x !== id),
          lastWriteAt: Date.now(),
        })),
      toggleSection: (kind) =>
        set((s) => ({
          collapsedSections: s.collapsedSections.includes(kind)
            ? s.collapsedSections.filter((x) => x !== kind)
            : [...s.collapsedSections, kind],
          lastWriteAt: Date.now(),
        })),
    }),
    {
      name: 'sidebar-tree-state:v1',
      version: 1,
      migrate: migrateSidebarTree,
      // SSR/hydration mismatch 방지 — Next.js 15 App Router 디폴트 storage(localStorage)
    },
  ),
)
