import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useMySharedWithMe } from './useMySharedWithMe'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { fetchMySharedWithMe: vi.fn() },
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useMySharedWithMe', () => {
  beforeEach(() => vi.clearAllMocks())

  it('성공 시 items 반환', async () => {
    ;(api.fetchMySharedWithMe as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [
        {
          permissionId: 'p1',
          resourceType: 'file',
          resourceId: 'f1',
          name: '계약서.pdf',
          preset: 'read',
          grantedAt: '2026-05-14T08:00:00Z',
          grantedBy: { id: 'u1', name: '김매니저' },
        },
      ],
      nextCursor: null,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useMySharedWithMe(5), {
      wrapper: makeWrapper(qc),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.items).toHaveLength(1)
    expect(result.current.data?.items[0].name).toBe('계약서.pdf')
    expect(api.fetchMySharedWithMe).toHaveBeenCalledWith(5)
  })

  it('401 시 isError + retry 미발생', async () => {
    ;(api.fetchMySharedWithMe as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 401,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useMySharedWithMe(), {
      wrapper: makeWrapper(qc),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(api.fetchMySharedWithMe).toHaveBeenCalledTimes(1)
  })
})
