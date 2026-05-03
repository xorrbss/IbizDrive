import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * AuthGuard — auth-must-change-pw P3.
 *
 * <p>핵심 가정: 로그인 사용자가 mustChangePassword=true이면서 현재 경로가
 * `/account/password`가 아니면 강제로 `/account/password?force=1`로 redirect.
 * 비밀번호 변경 페이지 자체에서는 redirect하지 않아야 무한 루프 회피.
 */

const replaceMock = vi.fn()
let pathnameMock = '/files'
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn(), back: vi.fn() }),
  usePathname: () => pathnameMock,
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

import { AuthGuard } from './AuthGuard'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const session = (mustChangePassword: boolean) => ({
  user: {
    id: 'u1',
    email: 'a@b.com',
    name: 'Alice',
    kind: 'human' as const,
    mustChangePassword,
  },
  departments: [],
  roles: [],
  effectivePermissionsCacheKey: 'k',
})

describe('AuthGuard — mustChangePassword guard (auth-must-change-pw P3)', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    useMeMock.mockReset()
    pathnameMock = '/files'
  })

  it('mustChangePassword=true && pathname=/files → /account/password?force=1로 redirect', async () => {
    pathnameMock = '/files'
    useMeMock.mockReturnValue({ data: session(true), isLoading: false, isError: false })
    wrap(
      <AuthGuard>
        <div>secret</div>
      </AuthGuard>,
    )
    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith('/account/password?force=1')
    })
  })

  it('mustChangePassword=true && pathname=/account/password → redirect 없음 (pass-through)', async () => {
    pathnameMock = '/account/password'
    useMeMock.mockReturnValue({ data: session(true), isLoading: false, isError: false })
    wrap(
      <AuthGuard>
        <div>secret</div>
      </AuthGuard>,
    )
    // 비동기 effect 적용 대기 후 호출 없어야 함
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('mustChangePassword=false 사용자는 redirect 없음', async () => {
    pathnameMock = '/files'
    useMeMock.mockReturnValue({ data: session(false), isLoading: false, isError: false })
    wrap(
      <AuthGuard>
        <div>secret</div>
      </AuthGuard>,
    )
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
  })
})
