import { describe, it, expect, beforeEach } from 'vitest'
import { useRenameUiStore } from './renameUi'

describe('useRenameUiStore', () => {
  beforeEach(() => {
    useRenameUiStore.setState({
      isOpen: false,
      targetId: null,
      targetName: '',
      error: null,
    })
  })

  it('open(id, name) → isOpen true, error reset', () => {
    useRenameUiStore.getState().setError('이전 에러')
    useRenameUiStore.getState().open('file_x', '예전이름.txt')
    const s = useRenameUiStore.getState()
    expect(s.isOpen).toBe(true)
    expect(s.targetId).toBe('file_x')
    expect(s.targetName).toBe('예전이름.txt')
    expect(s.error).toBeNull()
  })

  it('close() → 모든 필드 초기화', () => {
    useRenameUiStore.getState().open('file_x', 'a.txt')
    useRenameUiStore.getState().setError('붙은 에러')
    useRenameUiStore.getState().close()
    const s = useRenameUiStore.getState()
    expect(s.isOpen).toBe(false)
    expect(s.targetId).toBeNull()
    expect(s.targetName).toBe('')
    expect(s.error).toBeNull()
  })

  it('setError(msg) → error만 갱신, isOpen 유지', () => {
    useRenameUiStore.getState().open('file_x', 'a.txt')
    useRenameUiStore.getState().setError('충돌')
    const s = useRenameUiStore.getState()
    expect(s.error).toBe('충돌')
    expect(s.isOpen).toBe(true)
  })
})
