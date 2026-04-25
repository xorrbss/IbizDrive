import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useDebounce } from './useDebounce'

describe('useDebounce', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('초기값을 즉시 반영', () => {
    const { result } = renderHook(() => useDebounce('hello', 300))
    expect(result.current).toBe('hello')
  })

  it('지연 시간 전에는 이전 값 유지', () => {
    const { result, rerender } = renderHook(
      ({ v }: { v: string }) => useDebounce(v, 300),
      { initialProps: { v: 'a' } },
    )
    rerender({ v: 'b' })
    act(() => {
      vi.advanceTimersByTime(200)
    })
    expect(result.current).toBe('a')
  })

  it('지연 시간 후 새 값 반영', () => {
    const { result, rerender } = renderHook(
      ({ v }: { v: string }) => useDebounce(v, 300),
      { initialProps: { v: 'a' } },
    )
    rerender({ v: 'b' })
    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(result.current).toBe('b')
  })

  it('연속 변경 시 마지막 값만 반영', () => {
    const { result, rerender } = renderHook(
      ({ v }: { v: string }) => useDebounce(v, 300),
      { initialProps: { v: 'a' } },
    )
    rerender({ v: 'b' })
    act(() => {
      vi.advanceTimersByTime(100)
    })
    rerender({ v: 'c' })
    act(() => {
      vi.advanceTimersByTime(100)
    })
    rerender({ v: 'd' })
    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(result.current).toBe('d')
  })
})
