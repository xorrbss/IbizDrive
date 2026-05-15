import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * UserMenu — admin 링크 노출 (m-admin-entry-rewrite P4).
 *
 * <p>회귀: ADMIN role 보유자에게만 "관리자 페이지" 링크 노출.
 * displayName/email/로그아웃/비밀번호 변경 항목은 role 무관 보존.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

vi.mock('@/hooks/useLogout', () => ({
  useLogout: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

import { UserMenu } from './UserMenu'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const session = (roles: string[]) => ({
  user: {
    id: 'u1',
    email: 'alice@example.com',
    name: 'Alice',
    kind: 'human' as const,
    mustChangePassword: false,
  },
  departments: [],
  roles,
  effectivePermissionsCacheKey: 'k',
})

describe('UserMenu — admin 링크 (m-admin-entry-rewrite P4)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
  })

  it('ADMIN role → "관리자 페이지" 링크 노출 + /admin href', () => {
    useMeMock.mockReturnValue({ data: session(['ADMIN']), isLoading: false, isError: false })
    const { getByRole } = wrap(<UserMenu />)
    const link = getByRole('link', { name: /관리자 페이지/ })
    expect(link.getAttribute('href')).toBe('/admin')
  })

  it('MEMBER role → "관리자 페이지" 링크 미노출', () => {
    useMeMock.mockReturnValue({ data: session(['MEMBER']), isLoading: false, isError: false })
    const { queryByRole } = wrap(<UserMenu />)
    expect(queryByRole('link', { name: /관리자 페이지/ })).toBeNull()
  })

  it('회귀: displayName/email/로그아웃/비밀번호 변경 표시 보존 (MEMBER 기준)', () => {
    useMeMock.mockReturnValue({ data: session(['MEMBER']), isLoading: false, isError: false })
    const { getByText, getByRole } = wrap(<UserMenu />)
    expect(getByText('Alice')).not.toBeNull()
    expect(getByText('alice@example.com')).not.toBeNull()
    expect(getByRole('button', { name: '로그아웃' })).not.toBeNull()
    expect(getByRole('link', { name: '비밀번호 변경' })).not.toBeNull()
  })

  it('이름/이메일 영역 — /account Link wrap (마이 페이지 진입)', () => {
    useMeMock.mockReturnValue({ data: session(['MEMBER']), isLoading: false, isError: false })
    const { getByRole, getByText } = wrap(<UserMenu />)
    const link = getByRole('link', { name: '마이 페이지' })
    expect(link.getAttribute('href')).toBe('/account')
    expect(link.contains(getByText('Alice'))).toBe(true)
    expect(link.contains(getByText('alice@example.com'))).toBe(true)
  })
})
