import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { LoginClient } from './LoginClient'
import { api } from '@/lib/api'
import { useAuth } from '@/hooks/useAuth'

const replace = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => new URLSearchParams('next=/admin/audit/logs'),
}))

vi.mock('@/lib/api', () => ({
  api: {
    login: vi.fn(),
  },
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'LoginClientTestWrapper'
  return Wrapper
}

function mockAuth(value: Partial<ReturnType<typeof useAuth>>) {
  vi.mocked(useAuth).mockReturnValue(value as unknown as ReturnType<typeof useAuth>)
}

describe('LoginClient', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuth({
      isAuthenticated: false,
      isLoading: false,
      session: null,
    })
  })

  it('submits credentials and returns to safe next path', async () => {
    vi.mocked(api.login).mockResolvedValue({
      user: {
        id: 'u1',
        email: 'admin@example.com',
        name: '관리자',
        kind: 'human',
        mustChangePassword: false,
      },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'u1:ADMIN:v0',
    })

    render(<LoginClient />, { wrapper: wrapper() })

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'admin@example.com' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'Password1234' },
    })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    await waitFor(() => expect(api.login).toHaveBeenCalledWith({
      email: 'admin@example.com',
      password: 'Password1234',
    }))
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/admin/audit/logs'))
  })

  it('shows invalid credentials message without redirecting', async () => {
    const err = new Error('invalid') as Error & { reason: string; status: number }
    err.status = 401
    err.reason = 'INVALID_CREDENTIALS'
    vi.mocked(api.login).mockRejectedValue(err)

    render(<LoginClient />, { wrapper: wrapper() })

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'member@example.com' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'wrong' },
    })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect((await screen.findByRole('alert')).textContent).toContain(
      '이메일 또는 비밀번호가 올바르지 않습니다',
    )
    expect(replace).not.toHaveBeenCalled()
  })

  it('redirects an already authenticated user', async () => {
    mockAuth({
      isAuthenticated: true,
      isLoading: false,
      session: {
        user: {
          id: 'u1',
          email: 'admin@example.com',
          name: '관리자',
          kind: 'human',
          mustChangePassword: false,
        },
        departments: [],
        roles: ['ADMIN'],
        effectivePermissionsCacheKey: 'u1:ADMIN:v0',
      },
    })

    render(<LoginClient />, { wrapper: wrapper() })

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/admin/audit/logs'))
  })
})
