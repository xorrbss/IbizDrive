import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { AuthGate } from './AuthGate'
import { useAuth } from '@/hooks/useAuth'

const replace = vi.fn()
let pathname = '/files/root'
let searchParams = new URLSearchParams('file=f1')

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  usePathname: () => pathname,
  useSearchParams: () => searchParams,
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

function mockAuth(value: Partial<ReturnType<typeof useAuth>>) {
  vi.mocked(useAuth).mockReturnValue(value as unknown as ReturnType<typeof useAuth>)
}

describe('AuthGate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    pathname = '/files/root'
    searchParams = new URLSearchParams('file=f1')
  })

  it('shows a pending state while auth is loading', () => {
    mockAuth({
      isLoading: true,
      isAuthenticated: false,
      isUnauthenticated: false,
      roles: [],
    })

    render(
      <AuthGate>
        <span>protected</span>
      </AuthGate>,
    )

    expect(screen.getByRole('status').textContent).toContain('인증 확인 중')
    expect(screen.queryByText('protected')).toBeNull()
  })

  it('redirects unauthenticated users with the current path as next', async () => {
    mockAuth({
      isLoading: false,
      isAuthenticated: false,
      isUnauthenticated: true,
      roles: [],
    })

    render(
      <AuthGate>
        <span>protected</span>
      </AuthGate>,
    )

    await waitFor(() => {
      expect(replace).toHaveBeenCalledWith('/login?next=%2Ffiles%2Froot%3Ffile%3Df1')
    })
    expect(screen.queryByText('protected')).toBeNull()
  })

  it('blocks users without an allowed role', () => {
    mockAuth({
      isLoading: false,
      isAuthenticated: true,
      isUnauthenticated: false,
      roles: ['MEMBER'],
    })

    render(
      <AuthGate allowedRoles={['ADMIN', 'AUDITOR']}>
        <span>admin</span>
      </AuthGate>,
    )

    expect(screen.getByRole('alert').textContent).toContain('접근 권한이 없습니다')
    expect(screen.queryByText('admin')).toBeNull()
  })

  it('renders children for authenticated users with an allowed role', () => {
    mockAuth({
      isLoading: false,
      isAuthenticated: true,
      isUnauthenticated: false,
      roles: ['AUDITOR'],
    })

    render(
      <AuthGate allowedRoles={['ADMIN', 'AUDITOR']}>
        <span>admin</span>
      </AuthGate>,
    )

    expect(screen.getByText('admin')).toBeTruthy()
  })
})
