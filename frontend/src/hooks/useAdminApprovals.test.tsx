import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminApprovals } from './useAdminApprovals'
import * as apiModule from '@/lib/api'
import type { AdminApprovalPage } from '@/types/admin-approval'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    listAdminApprovals: vi.fn(),
  }
})

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const PAGE: AdminApprovalPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 50,
}

describe('useAdminApprovals', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('데이터 로딩 성공 — listAdminApprovals 호출 + 기본 page/size', async () => {
    ;(apiModule.listAdminApprovals as ReturnType<typeof vi.fn>).mockResolvedValue(
      PAGE,
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminApprovals(), {
      wrapper: wrap(qc),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiModule.listAdminApprovals).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 50 }),
    )
  })

  it('actionType 필터 전파', async () => {
    ;(apiModule.listAdminApprovals as ReturnType<typeof vi.fn>).mockResolvedValue(
      PAGE,
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAdminApprovals({ actionType: 'role_change' }), {
      wrapper: wrap(qc),
    })
    await waitFor(() =>
      expect(apiModule.listAdminApprovals).toHaveBeenCalled(),
    )
    const arg = (apiModule.listAdminApprovals as ReturnType<typeof vi.fn>).mock
      .calls[0][0]
    expect(arg.actionType).toBe('role_change')
  })

  it('filter 변경 시 query key 변화 → 재요청', async () => {
    const fn = apiModule.listAdminApprovals as ReturnType<typeof vi.fn>
    fn.mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    const { rerender } = renderHook(
      ({ actionType }: { actionType: 'role_change' | 'trash_purge' }) =>
        useAdminApprovals({ actionType }),
      {
        wrapper: wrap(qc),
        initialProps: { actionType: 'role_change' } as {
          actionType: 'role_change' | 'trash_purge'
        },
      },
    )
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(1))

    rerender({ actionType: 'trash_purge' })
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(2))
    expect(fn.mock.calls[1][0].actionType).toBe('trash_purge')
  })

  it('403 → isError + retry false', async () => {
    const err = Object.assign(new Error('listAdminApprovals failed: 403'), {
      status: 403,
    })
    const fn = apiModule.listAdminApprovals as ReturnType<typeof vi.fn>
    fn.mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminApprovals(), {
      wrapper: wrap(qc),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(fn).toHaveBeenCalledTimes(1)
  })
})
