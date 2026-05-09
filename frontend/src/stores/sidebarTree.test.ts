import { describe, it, expect, beforeEach } from 'vitest'
import { useSidebarTreeStore, migrateSidebarTree } from './sidebarTree'

describe('useSidebarTreeStore', () => {
  beforeEach(() => {
    useSidebarTreeStore.setState({
      expandedFolderIds: [],
      collapsedSections: [],
      lastWriteAt: Date.now(),
    })
  })

  it('toggles folder expansion', () => {
    const { toggleFolder } = useSidebarTreeStore.getState()
    toggleFolder('f1')
    expect(useSidebarTreeStore.getState().expandedFolderIds).toContain('f1')
    toggleFolder('f1')
    expect(useSidebarTreeStore.getState().expandedFolderIds).not.toContain('f1')
  })

  it('toggles section collapse', () => {
    const { toggleSection } = useSidebarTreeStore.getState()
    toggleSection('shared')
    expect(useSidebarTreeStore.getState().collapsedSections).toContain('shared')
  })

  it('expandFolder is idempotent', () => {
    const { expandFolder } = useSidebarTreeStore.getState()
    expandFolder('f1')
    expandFolder('f1')
    const ids = useSidebarTreeStore.getState().expandedFolderIds
    expect(ids.filter((x) => x === 'f1')).toHaveLength(1)
  })

  it('mutators update lastWriteAt', () => {
    const before = Date.now() - 1
    useSidebarTreeStore.getState().toggleFolder('f2')
    expect(useSidebarTreeStore.getState().lastWriteAt).toBeGreaterThanOrEqual(before)
  })
})

describe('migrateSidebarTree — 30-day TTL', () => {
  const THIRTY_ONE_DAYS = 31 * 24 * 3600 * 1000

  it('resets state when lastWriteAt is older than 30 days', () => {
    const stale = {
      expandedFolderIds: ['f1', 'f2'],
      collapsedSections: ['shared'],
      lastWriteAt: Date.now() - THIRTY_ONE_DAYS,
    }
    const result = migrateSidebarTree(stale) as Record<string, unknown>
    expect(result.expandedFolderIds).toEqual([])
    expect(result.collapsedSections).toEqual([])
    expect(typeof result.lastWriteAt).toBe('number')
  })

  it('preserves state when lastWriteAt is within 30 days', () => {
    const fresh = {
      expandedFolderIds: ['f1'],
      collapsedSections: [],
      lastWriteAt: Date.now() - 1000,
    }
    const result = migrateSidebarTree(fresh) as Record<string, unknown>
    expect(result.expandedFolderIds).toEqual(['f1'])
  })

  it('handles null/undefined persisted gracefully', () => {
    expect(migrateSidebarTree(null)).toBeNull()
    expect(migrateSidebarTree(undefined)).toBeUndefined()
  })
})
