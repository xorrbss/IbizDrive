import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminPermissions } from './useAdminPermissions'
import { api } from '@/lib/api'
import type { AdminPermissionPage } from '@/types/permission'

vi.mock('@/lib/api', () => ({
  api: {
    adminListPermissions: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const PAGE: AdminPermissionPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
}

describe('useAdminPermissions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('데이터 로딩 성공 — api.adminListPermissions 호출 + filter 정규화', async () => {
    ;(api.adminListPermissions as ReturnType<typeof vi.fn>).mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(
      () =>
        useAdminPermissions({
          subjectType: 'user',
          q: '  ALICE  ',
          page: 1,
          size: 50,
        }),
      { wrapper: wrap(qc) },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminListPermissions).toHaveBeenCalledWith(
      expect.objectContaining({
        subjectType: 'user',
        q: 'alice',
        page: 1,
        size: 50,
      }),
    )
  })

  it('빈 q/subjectId — undefined로 위임', async () => {
    ;(api.adminListPermissions as ReturnType<typeof vi.fn>).mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(
      () => useAdminPermissions({ q: '   ', subjectId: '' }),
      { wrapper: wrap(qc) },
    )

    await waitFor(() => expect(api.adminListPermissions).toHaveBeenCalled())
    const arg = (api.adminListPermissions as ReturnType<typeof vi.fn>).mock.calls[0][0]
    expect(arg.q).toBeUndefined()
    expect(arg.subjectId).toBeUndefined()
    expect(arg.page).toBe(0)
    expect(arg.size).toBe(20)
  })

  it('filter 변경 시 query key 변화 → 재요청', async () => {
    const fn = api.adminListPermissions as ReturnType<typeof vi.fn>
    fn.mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    const { rerender } = renderHook(
      ({ q }: { q: string }) => useAdminPermissions({ q }),
      { wrapper: wrap(qc), initialProps: { q: 'first' } },
    )
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(1))

    rerender({ q: 'second' })
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(2))
    expect(fn.mock.calls[1][0].q).toBe('second')
  })

  it('403 → isError + retry 비활성', async () => {
    const err = Object.assign(new Error('adminListPermissions failed: 403'), {
      status: 403,
    })
    const fn = api.adminListPermissions as ReturnType<typeof vi.fn>
    fn.mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminPermissions({}), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(fn).toHaveBeenCalledTimes(1)
  })
})
