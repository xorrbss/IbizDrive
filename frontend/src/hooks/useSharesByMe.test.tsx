import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSharesByMe } from './useSharesByMe'
import { api } from '@/lib/api'
import type { ShareDto } from '@/types/share'

vi.mock('@/lib/api', () => ({
  api: { listSharesByMe: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const SHARE: ShareDto = {
  id: 'sh-1',
  fileId: 'file-1',
  permissionId: 'perm-1',
  sharedBy: 'me',
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
  expiresAt: null,
  message: null,
  createdAt: '2026-04-30T12:00:00Z',
}

describe('useSharesByMe', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('초기 페이지: cursor 없이 호출, items + nextCursor surface', async () => {
    ;(api.listSharesByMe as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [SHARE],
      nextCursor: 'next-token',
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useSharesByMe(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.listSharesByMe).toHaveBeenCalledWith({ cursor: undefined })
    expect(result.current.data?.pages[0].items).toEqual([SHARE])
    expect(result.current.hasNextPage).toBe(true)
  })

  it('nextCursor=null 이면 hasNextPage=false', async () => {
    ;(api.listSharesByMe as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [],
      nextCursor: null,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useSharesByMe(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(false)
  })

  it('fetchNextPage → 두 번째 호출에 cursor 전달', async () => {
    const mock = api.listSharesByMe as ReturnType<typeof vi.fn>
    mock.mockImplementation(async ({ cursor }: { cursor?: string } = {}) => {
      if (!cursor) return { items: [SHARE], nextCursor: 'p2-token' }
      return { items: [SHARE], nextCursor: null }
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useSharesByMe(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)

    await act(async () => {
      await result.current.fetchNextPage()
    })

    await waitFor(() => expect(mock).toHaveBeenCalledTimes(2))
    expect(mock).toHaveBeenLastCalledWith({ cursor: 'p2-token' })
    expect(result.current.data?.pages.length).toBe(2)
    expect(result.current.hasNextPage).toBe(false)
  })
})
