import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSearch } from './useSearch'
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
 * Note: useFakeTimers는 useDebounce(setTimeout)와 mock api(setTimeout) 둘 다 가로챔.
 * waitFor는 내부 setInterval로 폴링 → fake timers와 충돌.
 *
 * 전략: api.searchFiles는 즉시 resolve하는 spy로 교체하고, debounce timer만 advance.
 * 결과 도착은 await act(async () => {})로 microtask flush.
 */

describe('useSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(api, 'searchFiles').mockResolvedValue({ items: [] })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('1자 입력은 enabled false → fetch 호출 없음', async () => {
    const spy = vi.mocked(api.searchFiles)
    const { result, rerender } = renderHook(
      ({ q }: { q: string }) => useSearch(q),
      {
        initialProps: { q: '' },
        wrapper: wrapper(freshClient()),
      },
    )
    rerender({ q: '가' })
    await act(async () => {
      vi.advanceTimersByTime(500)
    })
    expect(result.current.fetchStatus).toBe('idle')
    expect(spy).not.toHaveBeenCalled()
  })

  it('2자 이상 입력 + 300ms 경과 시 1회 호출', async () => {
    const spy = vi.mocked(api.searchFiles)
    const { rerender } = renderHook(({ q }: { q: string }) => useSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })
    rerender({ q: '계약' })
    expect(spy).not.toHaveBeenCalled()

    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    // microtask 한 번 더 flush
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy).toHaveBeenCalledWith(
      { q: '계약', filters: {} },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('정규화 적용 — 공백/대문자 → q는 normalize된 값', async () => {
    const spy = vi.mocked(api.searchFiles)
    const { rerender } = renderHook(({ q }: { q: string }) => useSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })
    rerender({ q: '  PDF  ' })

    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalled()
    expect(spy.mock.calls[0][0].q).toBe('pdf')
  })

  it('빠른 연속 입력 시 debounce로 마지막 query만 호출', async () => {
    const spy = vi.mocked(api.searchFiles)
    const { rerender } = renderHook(({ q }: { q: string }) => useSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })

    rerender({ q: '계' })
    await act(async () => {
      vi.advanceTimersByTime(100)
    })
    rerender({ q: '계약' })
    await act(async () => {
      vi.advanceTimersByTime(100)
    })
    rerender({ q: '계약서' })
    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledTimes(1)
    const lastCall = spy.mock.calls[0]
    expect(lastCall[0].q).toBe('계약서')
  })

  // 실제 mock api (setTimeout 200ms 포함)와 통합 동작 — 실제 타이머 사용
  // (fake timer + react-query 데이터 propagation은 까다로워 통합 테스트로 대체)
  it('integration: 실제 api.searchFiles와 동작 (real timers)', async () => {
    vi.useRealTimers()
    vi.restoreAllMocks()

    const { result, rerender } = renderHook(
      ({ q }: { q: string }) => useSearch(q),
      {
        initialProps: { q: '' },
        wrapper: wrapper(freshClient()),
      },
    )
    rerender({ q: '계약' })

    await waitFor(
      () => {
        expect(result.current.data?.items.some((f) => f.name.includes('계약'))).toBe(true)
      },
      { timeout: 2000 },
    )
  })
})
