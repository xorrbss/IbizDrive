import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useDepartmentSearch } from './useDepartmentSearch'
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
 * A16.5 — useDepartmentSearch는 useUserSearch와 동일 패턴 (debounce 300ms + minLen 2 +
 *  keepPreviousData + AbortSignal). normalize는 `q.trim().toLowerCase()` (A16 ADR #36 —
 *  A14 답습, NFC collapse 미사용).
 *
 * fake-timers 전략은 useUserSearch.test와 동일.
 */
describe('useDepartmentSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(api, 'searchDepartments').mockResolvedValue({ items: [] })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('1자 입력은 enabled false → fetch 호출 없음', async () => {
    const spy = vi.mocked(api.searchDepartments)
    const { result, rerender } = renderHook(
      ({ q }: { q: string }) => useDepartmentSearch(q),
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
    const spy = vi.mocked(api.searchDepartments)
    const { rerender } = renderHook(({ q }: { q: string }) => useDepartmentSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })
    rerender({ q: 'en' })
    expect(spy).not.toHaveBeenCalled()

    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy).toHaveBeenCalledWith(
      { q: 'en', limit: 20 },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('normalize: trim + lowercase (NFC collapse 미적용)', async () => {
    const spy = vi.mocked(api.searchDepartments)
    const { rerender } = renderHook(({ q }: { q: string }) => useDepartmentSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })
    rerender({ q: '  Engineering  ' })

    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalled()
    expect(spy.mock.calls[0][0].q).toBe('engineering')
  })

  it('빠른 연속 입력 시 debounce로 마지막 query만 호출', async () => {
    const spy = vi.mocked(api.searchDepartments)
    const { rerender } = renderHook(({ q }: { q: string }) => useDepartmentSearch(q), {
      initialProps: { q: '' },
      wrapper: wrapper(freshClient()),
    })

    rerender({ q: 'en' })
    await act(async () => {
      vi.advanceTimersByTime(100)
    })
    rerender({ q: 'eng' })
    await act(async () => {
      vi.advanceTimersByTime(100)
    })
    rerender({ q: 'engi' })
    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0][0].q).toBe('engi')
  })

  it('limit 옵션 override 전달', async () => {
    const spy = vi.mocked(api.searchDepartments)
    const { rerender } = renderHook(
      ({ q }: { q: string }) => useDepartmentSearch(q, { limit: 5 }),
      {
        initialProps: { q: '' },
        wrapper: wrapper(freshClient()),
      },
    )
    rerender({ q: 'de' })
    await act(async () => {
      vi.advanceTimersByTime(300)
    })
    await act(async () => {
      await Promise.resolve()
    })

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'de', limit: 5 }),
      expect.anything(),
    )
  })
})
