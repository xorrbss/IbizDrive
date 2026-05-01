import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSharesWithMe } from './useSharesWithMe'
import { api } from '@/lib/api'
import type { ShareDto } from '@/types/share'

vi.mock('@/lib/api', () => ({
  api: { listSharesWithMe: vi.fn() },
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
  folderId: null,
  permissionId: 'perm-1',
  sharedBy: 'someone',
  message: null,
  expiresAt: null,
  createdAt: '2026-04-30T12:00:00Z',
  revokedAt: null,
  revokedBy: null,
}

describe('useSharesWithMe', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('초기 페이지: cursor 없이 호출, items + nextCursor surface', async () => {
    ;(api.listSharesWithMe as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [SHARE],
      nextCursor: null,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useSharesWithMe(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.listSharesWithMe).toHaveBeenCalledWith({ cursor: undefined })
    expect(result.current.data?.pages[0].items).toEqual([SHARE])
    expect(result.current.hasNextPage).toBe(false)
  })

  it('fetchNextPage → cursor 전달', async () => {
    const mock = api.listSharesWithMe as ReturnType<typeof vi.fn>
    mock.mockImplementation(async ({ cursor }: { cursor?: string } = {}) => {
      if (!cursor) return { items: [SHARE], nextCursor: 'cur2' }
      return { items: [], nextCursor: null }
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useSharesWithMe(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)

    await act(async () => {
      await result.current.fetchNextPage()
    })

    await waitFor(() => expect(mock).toHaveBeenCalledTimes(2))
    expect(mock).toHaveBeenLastCalledWith({ cursor: 'cur2' })
    expect(result.current.data?.pages.length).toBe(2)
  })
})
