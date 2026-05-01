import { describe, it, expect, beforeEach } from 'vitest'
import { useShareUiStore } from './shareUi'

describe('useShareUiStore (M8 + F5)', () => {
  beforeEach(() => {
    useShareUiStore.getState().close()
  })

  it('초기 상태 — closed', () => {
    const s = useShareUiStore.getState()
    expect(s.isOpen).toBe(false)
    expect(s.target).toBeNull()
  })

  it('open(file target) → isOpen + target 세팅', () => {
    useShareUiStore.getState().open({ kind: 'file', id: 'f1', name: 'doc.pdf' })
    const s = useShareUiStore.getState()
    expect(s.isOpen).toBe(true)
    expect(s.target).toEqual({ kind: 'file', id: 'f1', name: 'doc.pdf' })
  })

  it('open(folder target) → kind 보존', () => {
    useShareUiStore.getState().open({ kind: 'folder', id: 'fol-1', name: 'reports' })
    const s = useShareUiStore.getState()
    expect(s.isOpen).toBe(true)
    expect(s.target).toEqual({ kind: 'folder', id: 'fol-1', name: 'reports' })
  })

  it('close() → 모든 필드 초기화', () => {
    useShareUiStore.getState().open({ kind: 'file', id: 'f1', name: 'doc.pdf' })
    useShareUiStore.getState().close()
    const s = useShareUiStore.getState()
    expect(s.isOpen).toBe(false)
    expect(s.target).toBeNull()
  })
})
