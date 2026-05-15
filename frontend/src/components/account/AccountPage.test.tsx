/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AccountPage } from './AccountPage'

const routerReplace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: routerReplace, push: vi.fn(), back: vi.fn() }),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

const logoutMutate = vi.fn()
vi.mock('@/hooks/useLogout', () => ({
  useLogout: () => ({ mutateAsync: logoutMutate, isPending: false }),
}))

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const session = (overrides: Partial<any> = {}) => ({
  user: {
    id: 'u1',
    email: 'alice@example.com',
    name: 'Alice',
    kind: 'human' as const,
    mustChangePassword: false,
  },
  departments: [],
  roles: ['MEMBER'],
  effectivePermissionsCacheKey: 'k',
  ...overrides,
})

describe('AccountPage — 골격 (h1 + states)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
  })

  it('h1 "마이 페이지" 노출', () => {
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    expect(screen.getByRole('heading', { level: 1, name: '마이 페이지' })).toBeTruthy()
  })

  it('loading 상태 — "불러오는 중…"', () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    wrap(<AccountPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('error 상태 — "정보를 불러올 수 없습니다."', () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: false, isError: true })
    wrap(<AccountPage />)
    expect(screen.getByText('정보를 불러올 수 없습니다.')).toBeTruthy()
  })
})

describe('AccountPage — 프로필 섹션', () => {
  beforeEach(() => {
    useMeMock.mockReset()
  })

  it('5 필드 노출 — 이름/이메일/계정유형/부서/역할', () => {
    useMeMock.mockReturnValue({
      data: session({
        user: {
          id: 'u1', email: 'alice@example.com', name: 'Alice',
          kind: 'human' as const, mustChangePassword: false,
        },
        departments: [
          { id: 'd1', name: '개발팀', path: '/회사/연구소/개발팀' },
        ],
        roles: ['ADMIN'],
      }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('alice@example.com')).toBeTruthy()
    expect(screen.getByText('일반')).toBeTruthy()
    expect(screen.getByText('개발팀')).toBeTruthy()
    expect(screen.getByText('ADMIN')).toBeTruthy()
  })

  it('kind=service → "서비스" 라벨', () => {
    useMeMock.mockReturnValue({
      data: session({
        user: { id: 'u1', email: 'svc@example.com', name: 'svc', kind: 'service' as const, mustChangePassword: false },
      }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.getByText('서비스')).toBeTruthy()
  })

  it('departments 비어있음 → "—" 표시', () => {
    useMeMock.mockReturnValue({
      data: session({ departments: [] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.getByText('—')).toBeTruthy()
  })

  it('department path 가 title 속성으로 노출 (tooltip 회귀 가드)', () => {
    useMeMock.mockReturnValue({
      data: session({
        departments: [{ id: 'd1', name: '개발팀', path: '/회사/연구소/개발팀' }],
      }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    const chip = screen.getByText('개발팀')
    expect(chip.getAttribute('title')).toBe('/회사/연구소/개발팀')
  })

  it('roles 다중 — 모두 chip 으로 노출', () => {
    useMeMock.mockReturnValue({
      data: session({ roles: ['ADMIN', 'MEMBER'] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.getByText('ADMIN')).toBeTruthy()
    expect(screen.getByText('MEMBER')).toBeTruthy()
  })
})

describe('AccountPage — 액션 섹션', () => {
  beforeEach(() => {
    useMeMock.mockReset()
    logoutMutate.mockReset()
    logoutMutate.mockResolvedValue(undefined)
    routerReplace.mockReset()
  })

  it('비밀번호 변경 링크 — href="/account/password"', () => {
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    const link = screen.getByRole('link', { name: '비밀번호 변경' })
    expect(link.getAttribute('href')).toBe('/account/password')
  })

  it('ADMIN role → "관리자 페이지" 링크 노출 + href="/admin"', () => {
    useMeMock.mockReturnValue({
      data: session({ roles: ['ADMIN'] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    const link = screen.getByRole('link', { name: '관리자 페이지' })
    expect(link.getAttribute('href')).toBe('/admin')
  })

  it('non-admin (MEMBER) → "관리자 페이지" 링크 미노출', () => {
    useMeMock.mockReturnValue({
      data: session({ roles: ['MEMBER'] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.queryByRole('link', { name: '관리자 페이지' })).toBeNull()
  })

  it('로그아웃 버튼 클릭 → useLogout.mutateAsync 호출 + router.replace("/login")', async () => {
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    const btn = screen.getByRole('button', { name: '로그아웃' })
    fireEvent.click(btn)
    await vi.waitFor(() => {
      expect(logoutMutate).toHaveBeenCalledTimes(1)
      expect(routerReplace).toHaveBeenCalledWith('/login')
    })
  })

  it('로그아웃 mutation 실패해도 router.replace("/login") 진행 (사용자 의도 우선)', async () => {
    logoutMutate.mockRejectedValue(new Error('network'))
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    await vi.waitFor(() => {
      expect(routerReplace).toHaveBeenCalledWith('/login')
    })
  })
})
