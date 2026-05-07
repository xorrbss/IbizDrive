import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminDashboardSummary } from './useAdminDashboardSummary'
import { api } from '@/lib/api'
import type { AdminDashboardSummaryResponse } from '@/types/admin'

vi.mock('@/lib/api', () => ({
  api: { adminGetDashboardSummary: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const RESP: AdminDashboardSummaryResponse = {
  summary: {
    users: { total: 12, active: 10 },
    departments: { total: 4, active: 4 },
    folders: { active: 25 },
    files: { active: 117, trashed: 3 },
    audit: { last24h: 42 },
    storage: { usedBytes: 1234567890 },
  },
}

describe('useAdminDashboardSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 summary 객체가 그대로 노출된다', async () => {
    ;(api.adminGetDashboardSummary as ReturnType<typeof vi.fn>).mockResolvedValue(RESP)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminDashboardSummary(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminGetDashboardSummary).toHaveBeenCalledTimes(1)
    expect(result.current.data).toEqual(RESP.summary)
  })

  it('403 → isError + retry 미수행 (admin 화면 즉시 노출 정책)', async () => {
    const err = Object.assign(new Error('403'), { status: 403 })
    const fn = api.adminGetDashboardSummary as ReturnType<typeof vi.fn>
    fn.mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminDashboardSummary(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(fn).toHaveBeenCalledTimes(1)
  })
})
