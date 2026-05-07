import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminStorageOverview } from './useAdminStorageOverview'
import { getAdminStorageOverview, type AdminStorageOverviewResponse } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    getAdminStorageOverview: vi.fn(),
  }
})

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const RESPONSE: AdminStorageOverviewResponse = {
  overview: {
    totalFiles: 123,
    totalVersions: 200,
    totalBytes: 10_485_760,
    trashedFiles: 5,
    trashedBytes: 2_048,
    orphanCleanup: {
      lastRunAt: '2026-05-06T14:30:00Z',
      lastDeletedCount: 7,
    },
  },
}

describe('useAdminStorageOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('queryKey가 qk.adminStorageOverview()와 일치', () => {
    expect(qk.adminStorageOverview()).toEqual(['explorer', 'admin', 'storage', 'overview'])
  })

  it('성공 → getAdminStorageOverview 호출 + 결과 반영', async () => {
    ;(getAdminStorageOverview as ReturnType<typeof vi.fn>).mockResolvedValue(RESPONSE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminStorageOverview(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getAdminStorageOverview).toHaveBeenCalledTimes(1)
    expect(result.current.data).toEqual(RESPONSE)
  })

  it('실패 → 에러 노출 (retry: false)', async () => {
    ;(getAdminStorageOverview as ReturnType<typeof vi.fn>).mockRejectedValue(
      Object.assign(new Error('forbidden'), { status: 403 }),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminStorageOverview(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(Error)
  })
})
