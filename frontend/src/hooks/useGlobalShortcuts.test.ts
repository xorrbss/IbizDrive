import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useGlobalShortcuts, FOCUS_SEARCH_EVENT } from './useGlobalShortcuts'

describe('useGlobalShortcuts', () => {
  let listener: ReturnType<typeof vi.fn>

  beforeEach(() => {
    listener = vi.fn()
    window.addEventListener(FOCUS_SEARCH_EVENT, listener)
  })

  afterEach(() => {
    window.removeEventListener(FOCUS_SEARCH_EVENT, listener)
  })

  it('"/" 키 → app:focus-search 이벤트 디스패치', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', { key: '/' })
    window.dispatchEvent(event)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('input 안에서 "/" → 무시', () => {
    renderHook(() => useGlobalShortcuts())
    const input = document.createElement('input')
    document.body.appendChild(input)
    const event = new KeyboardEvent('keydown', { key: '/' })
    Object.defineProperty(event, 'target', { value: input })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
    document.body.removeChild(input)
  })

  it('textarea 안에서 "/" → 무시', () => {
    renderHook(() => useGlobalShortcuts())
    const ta = document.createElement('textarea')
    document.body.appendChild(ta)
    const event = new KeyboardEvent('keydown', { key: '/' })
    Object.defineProperty(event, 'target', { value: ta })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
    document.body.removeChild(ta)
  })

  it('contenteditable 안에서 "/" → 무시', () => {
    renderHook(() => useGlobalShortcuts())
    const div = document.createElement('div')
    div.setAttribute('contenteditable', 'true')
    document.body.appendChild(div)
    const event = new KeyboardEvent('keydown', { key: '/' })
    Object.defineProperty(event, 'target', { value: div })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
    document.body.removeChild(div)
  })

  it('Ctrl+/ → 무시 (modifier 있음)', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', { key: '/', ctrlKey: true })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
  })

  it('Meta+/ → 무시', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', { key: '/', metaKey: true })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
  })

  it('Meta+K (mac ⌘K) → app:focus-search 이벤트 디스패치', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', { key: 'k', metaKey: true })
    window.dispatchEvent(event)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('Ctrl+K (win/linux) → app:focus-search 이벤트 디스패치', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true })
    window.dispatchEvent(event)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('Ctrl+K 안에 input target이어도 디스패치 (전역 단축키)', () => {
    renderHook(() => useGlobalShortcuts())
    const input = document.createElement('input')
    document.body.appendChild(input)
    const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true })
    Object.defineProperty(event, 'target', { value: input })
    window.dispatchEvent(event)
    expect(listener).toHaveBeenCalledTimes(1)
    document.body.removeChild(input)
  })

  it('K (modifier 없음) → 무시', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', { key: 'k' })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
  })

  it('Shift+Ctrl+K → 무시 (extra modifier)', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', {
      key: 'k',
      ctrlKey: true,
      shiftKey: true,
    })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
  })

  it('Alt+Ctrl+K → 무시 (extra modifier)', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', {
      key: 'k',
      ctrlKey: true,
      altKey: true,
    })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
  })

  it('대문자 K + Ctrl → 무시 (shift 조합으로 간주)', () => {
    renderHook(() => useGlobalShortcuts())
    const event = new KeyboardEvent('keydown', {
      key: 'K',
      ctrlKey: true,
      shiftKey: true,
    })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
  })

  it('unmount 시 listener 제거', () => {
    const { unmount } = renderHook(() => useGlobalShortcuts())
    unmount()
    const event = new KeyboardEvent('keydown', { key: '/' })
    window.dispatchEvent(event)
    expect(listener).not.toHaveBeenCalled()
  })
})
