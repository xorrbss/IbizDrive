import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * AdminGuard — m-admin-entry-rewrite P1 + wave1.5-auditor-admin-ui-access.
 *
 * <p>UX-only role 가드 (보안의 진실은 백엔드 `@PreAuthorize`, docs/04 §1).
 * AuthGuard 안쪽에 중첩되는 전제이므로 비로그인(data===null)은 처리하지 않는다.
 * - data undefined (로딩) → null + redirect 없음
 * - data && roles ∩ allowedRoles === ∅ → router.replace('/files')
 * - data && roles ∩ allowedRoles ≠ ∅ → children
 *
 * <p>allowedRoles 기본값 ['ADMIN'] — 기존 테스트 회귀 가드.
 * AUDITOR 진입 허용 시나리오는 allowedRoles=['ADMIN','AUDITOR'].
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

describe('AdminGuard — default(ADMIN-only) UX 가드 회귀 (m-admin-entry-rewrite P1)', () => {
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

  it('default(ADMIN-only) + roles=[AUDITOR] → /files redirect (이중 가드 시나리오)', async () => {
    useMeMock.mockReturnValue({ data: session(['AUDITOR']), isLoading: false, isError: false })
    const { queryByText } = wrap(
      <AdminGuard>
        <div>admin-only-content</div>
      </AdminGuard>,
    )
    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith('/files')
    })
    expect(queryByText('admin-only-content')).toBeNull()
  })
})

describe('AdminGuard — allowedRoles=[ADMIN,AUDITOR] (wave1.5-auditor-admin-ui-access)', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    useMeMock.mockReset()
  })

  it('roles=[AUDITOR] → children 렌더 + redirect 없음', async () => {
    useMeMock.mockReturnValue({ data: session(['AUDITOR']), isLoading: false, isError: false })
    const { queryByText } = wrap(
      <AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>
        <div>read-only-area</div>
      </AdminGuard>,
    )
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
    expect(queryByText('read-only-area')).not.toBeNull()
  })

  it('roles=[ADMIN] → children 렌더 (회귀)', async () => {
    useMeMock.mockReturnValue({ data: session(['ADMIN']), isLoading: false, isError: false })
    const { queryByText } = wrap(
      <AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>
        <div>read-only-area</div>
      </AdminGuard>,
    )
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
    expect(queryByText('read-only-area')).not.toBeNull()
  })

  it('roles=[MEMBER] → /files redirect', async () => {
    useMeMock.mockReturnValue({ data: session(['MEMBER']), isLoading: false, isError: false })
    const { queryByText } = wrap(
      <AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>
        <div>read-only-area</div>
      </AdminGuard>,
    )
    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith('/files')
    })
    expect(queryByText('read-only-area')).toBeNull()
  })

  it('roles=[ADMIN, AUDITOR] (둘 다) → children 렌더', async () => {
    useMeMock.mockReturnValue({
      data: session(['ADMIN', 'AUDITOR']),
      isLoading: false,
      isError: false,
    })
    const { queryByText } = wrap(
      <AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>
        <div>read-only-area</div>
      </AdminGuard>,
    )
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
    expect(queryByText('read-only-area')).not.toBeNull()
  })
})
