import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * LoginPage — auth-must-change-pw P2.
 *
 * <p>핵심 가정: useMe.data.user.mustChangePassword === true이면 LoginPage가
 * `next` query를 무시하고 `/account/password?force=1`로 router.replace한다.
 *
 * <p>useRouter/useSearchParams는 next/navigation에서 가져오므로 모듈 단위로 mock.
 * useMe/useLogin은 hook 모듈 단위로 mock — 실제 API 호출 회피.
 */

const replaceMock = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

const mutateAsyncMock = vi.fn()
vi.mock('@/hooks/useLogin', () => ({
  useLogin: () => ({
    mutateAsync: mutateAsyncMock,
    isPending: false,
  }),
}))

import LoginPage from './page'

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

describe('LoginPage — mustChangePassword UX (auth-must-change-pw P2)', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mutateAsyncMock.mockReset()
    useMeMock.mockReset()
  })

  it('me.data.user.mustChangePassword=true 이면 /account/password?force=1로 redirect', async () => {
    useMeMock.mockReturnValue({ data: session(true), isLoading: false, isError: false })
    wrap(<LoginPage />)
    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith('/account/password?force=1')
    })
  })

  it('me.data.user.mustChangePassword=false 이면 next(/files)로 redirect', async () => {
    useMeMock.mockReturnValue({ data: session(false), isLoading: false, isError: false })
    wrap(<LoginPage />)
    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith('/files')
    })
  })

  it('미로그인(me.data=null) 시 자동 redirect 없음', async () => {
    useMeMock.mockReturnValue({ data: null, isLoading: false, isError: false })
    wrap(<LoginPage />)
    // 비동기 effect 적용 후에도 호출 없음 — 짧은 대기 후 검증
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
  })
})
