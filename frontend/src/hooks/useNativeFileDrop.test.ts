import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useNativeFileDrop } from './useNativeFileDrop'

function makeDataTransfer(files: File[], types: string[] = ['Files']): DataTransfer {
  return {
    files: Object.assign(files, { item: (i: number) => files[i] }),
    types,
    dropEffect: 'copy',
    effectAllowed: 'all',
  } as unknown as DataTransfer
}

function fireDragEvent(el: HTMLElement, type: string, dt: DataTransfer) {
  const e = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(e, 'dataTransfer', { value: dt })
  el.dispatchEvent(e)
}

describe('useNativeFileDrop', () => {
  let el: HTMLDivElement
  const onDrop = vi.fn()

  beforeEach(() => {
    onDrop.mockReset()
    el = document.createElement('div')
    document.body.appendChild(el)
  })
  afterEach(() => {
    document.body.innerHTML = ''
  })

  function render() {
    const ref = { current: el } as { current: HTMLDivElement | null }
    return renderHook(() => useNativeFileDrop(ref, onDrop))
  }

  it('types=Files이면 dragenter에서 isDragging=true', () => {
    const { result } = render()
    const dt = makeDataTransfer([new File([''], 'a.txt')])
    act(() => {
      fireDragEvent(el, 'dragenter', dt)
    })
    expect(result.current).toBe(true)
  })

  it('types에 Files 없음은 무시 (dnd-kit 이동)', () => {
    const { result } = render()
    const dt = makeDataTransfer([], ['application/x-dnd-kit'])
    act(() => {
      fireDragEvent(el, 'dragenter', dt)
    })
    expect(result.current).toBe(false)
  })

  it('drop → onDrop(files) 호출 + isDragging=false', () => {
    const { result } = render()
    const files = [new File([''], 'a.txt'), new File([''], 'b.txt')]
    const dt = makeDataTransfer(files)
    act(() => {
      fireDragEvent(el, 'dragenter', dt)
    })
    act(() => {
      fireDragEvent(el, 'drop', dt)
    })
    const called = onDrop.mock.calls[0][0] as File[]
    expect(called).toHaveLength(2)
    expect(called[0].name).toBe('a.txt')
    expect(called[1].name).toBe('b.txt')
    expect(result.current).toBe(false)
  })

  it('중첩 dragenter/leave 카운터', () => {
    const { result } = render()
    const dt = makeDataTransfer([new File([''], 'a.txt')])
    act(() => {
      fireDragEvent(el, 'dragenter', dt)
    })
    act(() => {
      fireDragEvent(el, 'dragenter', dt)
    })
    act(() => {
      fireDragEvent(el, 'dragleave', dt)
    })
    expect(result.current).toBe(true)
    act(() => {
      fireDragEvent(el, 'dragleave', dt)
    })
    expect(result.current).toBe(false)
  })
})
