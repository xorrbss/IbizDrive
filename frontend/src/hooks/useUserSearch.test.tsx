import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useUserSearch } from './useUserSearch'
import { api } from '@/lib/api'

function wrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'TestQueryWrapper'
  return Wrapper
}

function freshClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
}

/**
 * F6.2 — useUserSearch는 useSearch와 동일 패턴 (debounce 300ms + minLen 2 + keepPreviousData
 *  + AbortSignal). normalize는 `q.trim().toLowerCase()` (A14 ADR #35 — NFC collapse 미사용).
 *
 * fake-timers 전략은 useSearch.test와 동일.
 */
describe('useUserSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(api, 'searchUsers').mockResolvedValue({ items: [] })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('1자 입력은 enabled false → fetch 호출 없음', async () => {
    const spy = vi.mocked(api.searchUsers)
    const { result, rerender } = renderHook(
      ({ q }: { q: string }) => useUserSearch(q),
      {
        initialProps: { q: '' },
        wrapper: wrapper(freshClient()),
      },
    )
    rerender({ q: 'a' })
    await act(async () => {
      vi.advanceTimersByTime(500)
    })
    expect(result.current.fetchStatus).toBe('idle')
    expect(spy).not.toHaveBeenCalled()
  })

  it('2자 이상 입력 + 300ms debounce 후 1회 호출 (signal 포함)', async () => {
    const spy = vi.mocked(api.searchUsers)
    const { rerender } = renderHook(({ q }: { q: string }) => useUserSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })
    rerender({ q: 'al' })
    expect(spy).not.toHaveBeenCalled()

    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy).toHaveBeenCalledWith(
      { q: 'al', limit: 20 },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('normalize: trim + lowercase (NFC collapse 미적용)', async () => {
    const spy = vi.mocked(api.searchUsers)
    const { rerender } = renderHook(({ q }: { q: string }) => useUserSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })
    rerender({ q: '  Alice  ' })

    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalled()
    expect(spy.mock.calls[0][0].q).toBe('alice')
  })

  it('빠른 연속 입력 시 debounce로 마지막 query만 호출', async () => {
    const spy = vi.mocked(api.searchUsers)
    const { rerender } = renderHook(({ q }: { q: string }) => useUserSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })

    rerender({ q: 'al' })
    await act(async () => {
      vi.advanceTimersByTime(100)
    })
    rerender({ q: 'ali' })
    await act(async () => {
      vi.advanceTimersByTime(100)
    })
    rerender({ q: 'alic' })
    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0][0].q).toBe('alic')
  })

  it('limit 옵션 override 전달', async () => {
    const spy = vi.mocked(api.searchUsers)
    const { rerender } = renderHook(
      ({ q }: { q: string }) => useUserSearch(q, { limit: 5 }),
      {
        initialProps: { q: '' },
        wrapper: wrapper(freshClient()),
      },
    )
    rerender({ q: 'bo' })
    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'bo', limit: 5 }),
      expect.anything(),
    )
  })
})
