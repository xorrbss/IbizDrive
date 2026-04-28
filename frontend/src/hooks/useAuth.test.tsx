import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAuth } from './useAuth'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: {
    getMe: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'AuthTestQueryWrapper'
  return Wrapper
}

describe('useAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads current user from api.getMe', async () => {
    ;(api.getMe as ReturnType<typeof vi.fn>).mockResolvedValue({
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
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    const { result } = renderHook(() => useAuth(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isAuthenticated).toBe(true))
    expect(api.getMe).toHaveBeenCalledTimes(1)
    expect(result.current.user?.email).toBe('admin@example.com')
    expect(result.current.hasRole('ADMIN')).toBe(true)
    expect(result.current.hasRole('MEMBER')).toBe(false)
  })

  it('does not retry 401 /me failures', async () => {
    const err = new Error('unauthorized') as Error & { status: number }
    err.status = 401
    ;(api.getMe as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: 3 } } })

    const { result } = renderHook(() => useAuth(), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isUnauthenticated).toBe(true))
    expect(api.getMe).toHaveBeenCalledTimes(1)
  })
})
