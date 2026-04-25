import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useViewParam } from './useViewParam'

const replaceMock = vi.fn()
let mockQuery = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

describe('useViewParam', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mockQuery = ''
  })

  it('default → list', () => {
    const { result } = renderHook(() => useViewParam())
    expect(result.current.view).toBe('list')
  })

  it('?view=grid → grid', () => {
    mockQuery = 'view=grid'
    const { result } = renderHook(() => useViewParam())
    expect(result.current.view).toBe('grid')
  })

  it('setView(grid) → router.replace, setView(list) → ?view 제거', () => {
    const { result } = renderHook(() => useViewParam())
    act(() => result.current.setView('grid'))
    expect(replaceMock).toHaveBeenCalledWith('/files/root?view=grid', {
      scroll: false,
    })
    replaceMock.mockReset()
    mockQuery = 'view=grid&q=foo'
    const { result: r2 } = renderHook(() => useViewParam())
    act(() => r2.current.setView('list'))
    expect(replaceMock.mock.calls[0][0]).toBe('/files/root?q=foo')
  })
})
