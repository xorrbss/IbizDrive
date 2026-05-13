import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import * as apiModule from '@/lib/api'
import type { AdminApprovalPage } from '@/types/admin-approval'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    listAdminApprovals: vi.fn(),
  }
})

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

import { useAdminPendingApprovalsCount } from './useAdminPendingApprovalsCount'

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const session = (roles: string[]) => ({
  data: { user: { id: 'u1' }, departments: [], roles, effectivePermissionsCacheKey: 'k' },
  isLoading: false,
  isError: false,
})

const pageWithTotal = (totalElements: number): AdminApprovalPage => ({
  content: [],
  totalElements,
  totalPages: totalElements > 0 ? 1 : 0,
  number: 0,
  size: 1,
})

describe('useAdminPendingApprovalsCount', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useMeMock.mockReset()
  })

  it('ADMIN: totalElements를 count로 반환 (size=1 호출)', async () => {
    useMeMock.mockReturnValue(session(['ADMIN']))
    ;(apiModule.listAdminApprovals as ReturnType<typeof vi.fn>).mockResolvedValue(
      pageWithTotal(7),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminPendingApprovalsCount(), {
      wrapper: wrap(qc),
    })

    await waitFor(() => expect(result.current.count).toBe(7))
    expect(apiModule.listAdminApprovals).toHaveBeenCalledWith({ size: 1, page: 0 })
    expect(result.current.isError).toBe(false)
  })

  it('비-ADMIN (AUDITOR): API 호출 없음 + count=0', async () => {
    useMeMock.mockReturnValue(session(['AUDITOR']))
    const fn = apiModule.listAdminApprovals as ReturnType<typeof vi.fn>
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminPendingApprovalsCount(), {
      wrapper: wrap(qc),
    })

    // 비-ADMIN은 enabled:false → fetch가 호출되지 않음
    expect(result.current.count).toBe(0)
    expect(result.current.isLoading).toBe(false)
    expect(fn).not.toHaveBeenCalled()
  })

  it('비-ADMIN (MEMBER): API 호출 없음 + count=0', () => {
    useMeMock.mockReturnValue(session(['MEMBER']))
    const fn = apiModule.listAdminApprovals as ReturnType<typeof vi.fn>
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminPendingApprovalsCount(), {
      wrapper: wrap(qc),
    })

    expect(result.current.count).toBe(0)
    expect(fn).not.toHaveBeenCalled()
  })

  it('useMe 로딩 중 (data undefined) → 호출 없음 + count=0', () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    const fn = apiModule.listAdminApprovals as ReturnType<typeof vi.fn>
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminPendingApprovalsCount(), {
      wrapper: wrap(qc),
    })

    expect(result.current.count).toBe(0)
    expect(fn).not.toHaveBeenCalled()
  })

  it('ADMIN + 0건 → count=0 (배지 미렌더 분기)', async () => {
    useMeMock.mockReturnValue(session(['ADMIN']))
    ;(apiModule.listAdminApprovals as ReturnType<typeof vi.fn>).mockResolvedValue(
      pageWithTotal(0),
    )
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminPendingApprovalsCount(), {
      wrapper: wrap(qc),
    })

    await waitFor(() => expect(apiModule.listAdminApprovals).toHaveBeenCalled())
    expect(result.current.count).toBe(0)
  })

  it('403 에러 → isError true + count=0 (retry false)', async () => {
    useMeMock.mockReturnValue(session(['ADMIN']))
    const err = Object.assign(new Error('listAdminApprovals failed: 403'), {
      status: 403,
    })
    const fn = apiModule.listAdminApprovals as ReturnType<typeof vi.fn>
    fn.mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminPendingApprovalsCount(), {
      wrapper: wrap(qc),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.count).toBe(0)
    expect(fn).toHaveBeenCalledTimes(1)
  })
})
