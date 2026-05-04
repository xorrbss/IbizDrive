import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * AdminGuard — m-admin-entry-rewrite P1.
 *
 * <p>UX-only role 가드 (보안의 진실은 백엔드 `@PreAuthorize`, docs/04 §1).
 * AuthGuard 안쪽에 중첩되는 전제이므로 비로그인(data===null)은 처리하지 않는다.
 * - data undefined (로딩) → null + redirect 없음
 * - data && !roles.includes('ADMIN') → router.replace('/files')
 * - data && roles.includes('ADMIN') → children
 */

const replaceMock = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn(), back: vi.fn() }),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

import { AdminGuard } from './AdminGuard'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const session = (roles: string[]) => ({
  user: {
    id: 'u1',
    email: 'a@b.com',
    name: 'Alice',
    kind: 'human' as const,
    mustChangePassword: false,
  },
  departments: [],
  roles,
  effectivePermissionsCacheKey: 'k',
})

describe('AdminGuard — role=ADMIN UX 가드 (m-admin-entry-rewrite P1)', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    useMeMock.mockReset()
  })

  it('useMe loading (data undefined) → null 렌더 + router 호출 없음', async () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    const { queryByText } = wrap(
      <AdminGuard>
        <div>admin-content</div>
      </AdminGuard>,
    )
    expect(queryByText('admin-content')).toBeNull()
    // effect가 router.replace를 호출하지 않아야 함
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('roles=[MEMBER] → /files redirect', async () => {
    useMeMock.mockReturnValue({ data: session(['MEMBER']), isLoading: false, isError: false })
    const { queryByText } = wrap(
      <AdminGuard>
        <div>admin-content</div>
      </AdminGuard>,
    )
    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith('/files')
    })
    expect(queryByText('admin-content')).toBeNull()
  })

  it('roles=[ADMIN] → children 렌더 + redirect 없음', async () => {
    useMeMock.mockReturnValue({ data: session(['ADMIN']), isLoading: false, isError: false })
    const { queryByText } = wrap(
      <AdminGuard>
        <div>admin-content</div>
      </AdminGuard>,
    )
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
    expect(queryByText('admin-content')).not.toBeNull()
  })
})
