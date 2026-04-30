import { describe, it, expect, beforeEach } from 'vitest'
import { useShareUiStore } from './shareUi'

describe('useShareUiStore (M8)', () => {
  beforeEach(() => {
    useShareUiStore.getState().close()
  })

  it('초기 상태 — closed', () => {
    const s = useShareUiStore.getState()
    expect(s.isOpen).toBe(false)
    expect(s.fileId).toBeNull()
    expect(s.fileName).toBe('')
  })

  it('open(id, name) → isOpen + fileId + fileName 세팅', () => {
    useShareUiStore.getState().open('f1', 'doc.pdf')
    const s = useShareUiStore.getState()
    expect(s.isOpen).toBe(true)
    expect(s.fileId).toBe('f1')
    expect(s.fileName).toBe('doc.pdf')
  })

  it('close() → 모든 필드 초기화', () => {
    useShareUiStore.getState().open('f1', 'doc.pdf')
    useShareUiStore.getState().close()
    const s = useShareUiStore.getState()
    expect(s.isOpen).toBe(false)
    expect(s.fileId).toBeNull()
    expect(s.fileName).toBe('')
  })
})
