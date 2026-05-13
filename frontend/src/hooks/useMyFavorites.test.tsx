import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useMyFavorites } from './useMyFavorites'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { listMyFavorites: vi.fn() },
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useMyFavorites', () => {
  beforeEach(() => vi.clearAllMocks())

  it('성공 시 items 반환', async () => {
    ;(api.listMyFavorites as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [
        {
          resourceType: 'folder',
          resourceId: 'f1',
          name: '영업팀',
          parentId: null,
          scope: { type: 'department', id: 'd1' },
          starredAt: '2026-05-14T00:00:00Z',
        },
      ],
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useMyFavorites(), {
      wrapper: makeWrapper(qc),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.items).toHaveLength(1)
    expect(result.current.data?.items[0].name).toBe('영업팀')
  })

  it('401 시 isError + retry 미발생', async () => {
    ;(api.listMyFavorites as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 401,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useMyFavorites(), {
      wrapper: makeWrapper(qc),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(api.listMyFavorites).toHaveBeenCalledTimes(1)
  })
})
