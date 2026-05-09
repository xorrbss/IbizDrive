import { describe, it, expect, beforeEach } from 'vitest'
import { useSidebarTreeStore } from './sidebarTree'

describe('useSidebarTreeStore', () => {
  beforeEach(() => {
    useSidebarTreeStore.setState({
      expandedFolderIds: [],
      collapsedSections: [],
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
})
