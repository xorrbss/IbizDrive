import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useRef } from 'react'
import { useGridColumns } from './useGridColumns'

// jsdom에는 ResizeObserver가 없다 — 본 테스트만 자체 mock.
// (전역 setup에 두지 않는 이유: 다른 테스트가 ResizeObserver를 다른 방식으로 모킹해야 할 가능성 보존)
type ROCallback = (entries: Array<{ contentRect: { width: number; height: number } }>) => void

let lastInstance: { trigger: (width: number) => void } | null = null

beforeEach(() => {
  lastInstance = null
  class FakeResizeObserver {
    private cb: ROCallback
    constructor(cb: ROCallback) {
      this.cb = cb
      lastInstance = {
        trigger: (width: number) => {
          this.cb([{ contentRect: { width, height: 100 } }])
        },
      }
    }
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  ;(globalThis as unknown as { ResizeObserver: typeof FakeResizeObserver }).ResizeObserver =
    FakeResizeObserver
})

afterEach(() => {
  vi.restoreAllMocks()
})

/**
 * Wrapper hook: ref를 만들고 element clientWidth를 조작 가능하도록 한 뒤
 * useGridColumns 결과를 반환한다.
 */
function useGridColumnsWithFakeRef(initialWidth: number, opts: { minColWidth: number; gap: number }) {
  const ref = useRef<HTMLDivElement | null>(null)
  // 첫 렌더에 ref.current를 가짜 element로 채운다.
  if (ref.current === null) {
    const el = document.createElement('div')
    Object.defineProperty(el, 'clientWidth', {
      configurable: true,
      get: () => initialWidth,
    })
    ref.current = el
  }
  return useGridColumns(ref, opts)
}

describe('useGridColumns (M16V.1)', () => {
  it('width=300, min=140, gap=12 → columns=2', () => {
    // (300+12)/(140+12) = 2.05 → floor = 2
    const { result } = renderHook(() =>
      useGridColumnsWithFakeRef(300, { minColWidth: 140, gap: 12 }),
    )
    expect(result.current).toBe(2)
  })

  it('width=900, min=140, gap=12 → columns=6', () => {
    // (900+12)/(140+12) = 912/152 = 6.0 → floor = 6
    const { result } = renderHook(() =>
      useGridColumnsWithFakeRef(900, { minColWidth: 140, gap: 12 }),
    )
    expect(result.current).toBe(6)
  })

  it('width=100 (less than minColWidth) → columns=1 (Math.max 보장)', () => {
    const { result } = renderHook(() =>
      useGridColumnsWithFakeRef(100, { minColWidth: 140, gap: 12 }),
    )
    expect(result.current).toBe(1)
  })

  it('ResizeObserver callback이 width 변경을 전달하면 columns가 재계산된다', () => {
    const { result } = renderHook(() =>
      useGridColumnsWithFakeRef(300, { minColWidth: 140, gap: 12 }),
    )
    expect(result.current).toBe(2)

    act(() => {
      lastInstance?.trigger(900)
    })

    expect(result.current).toBe(6)
  })
})
