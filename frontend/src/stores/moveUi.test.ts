import { describe, it, expect, beforeEach } from 'vitest'
import { useMoveUiStore } from './moveUi'

describe('moveUi store', () => {
  beforeEach(() => {
    useMoveUiStore.setState({
      isMoveDialogOpen: false,
      moveIds: [],
      moveSourceFolderId: null,
    })
  })

  it('초기 상태', () => {
    const s = useMoveUiStore.getState()
    expect(s.isMoveDialogOpen).toBe(false)
    expect(s.moveIds).toEqual([])
    expect(s.moveSourceFolderId).toBeNull()
  })

  it('openMoveDialog가 상태를 설정한다', () => {
    useMoveUiStore.getState().openMoveDialog(['a', 'b'], 'folder_x')
    const s = useMoveUiStore.getState()
    expect(s.isMoveDialogOpen).toBe(true)
    expect(s.moveIds).toEqual(['a', 'b'])
    expect(s.moveSourceFolderId).toBe('folder_x')
  })

  it('closeMoveDialog가 리셋한다', () => {
    useMoveUiStore.getState().openMoveDialog(['a'], 'x')
    useMoveUiStore.getState().closeMoveDialog()
    const s = useMoveUiStore.getState()
    expect(s.isMoveDialogOpen).toBe(false)
    expect(s.moveIds).toEqual([])
    expect(s.moveSourceFolderId).toBeNull()
  })
})
