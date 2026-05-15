/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AccountPage } from './AccountPage'

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
