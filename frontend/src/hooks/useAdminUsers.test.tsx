import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAdminUsers } from './useAdminUsers'
import { api, type AdminUserPage } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { adminListUsers: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const PAGE: AdminUserPage = {
  content: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'ADMIN',
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
      lastLoginAt: null,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
}

describe('useAdminUsers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('데이터 로딩 성공 → api.adminListUsers 호출 + 결과 반영', async () => {
    ;(api.adminListUsers as ReturnType<typeof vi.fn>).mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminUsers(0, 50), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminListUsers).toHaveBeenCalledWith(0, 50, '')
    expect(result.current.data).toEqual(PAGE)
  })

  it('q 파라미터를 api.adminListUsers에 전달 (admin-user-search-update)', async () => {
    ;(api.adminListUsers as ReturnType<typeof vi.fn>).mockResolvedValue(PAGE)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminUsers(0, 50, 'alice'), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.adminListUsers).toHaveBeenCalledWith(0, 50, 'alice')
  })

  it('403 → isError + retry 비활성', async () => {
    const err = Object.assign(new Error('adminListUsers failed: 403'), { status: 403 })
    const fn = api.adminListUsers as ReturnType<typeof vi.fn>
    fn.mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminUsers(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(fn).toHaveBeenCalledTimes(1)
  })
})
