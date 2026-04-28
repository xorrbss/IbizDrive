import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TopBar } from './TopBar'
import { api } from '@/lib/api'
import { useAuth } from '@/hooks/useAuth'

const push = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push }),
}))

vi.mock('@/lib/api', () => ({
  api: {
    logout: vi.fn(),
  },
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

function wrapper() {
  const qc = new QueryClient()
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'TopBarTestWrapper'
  return Wrapper
}

function mockAuth(value: Partial<ReturnType<typeof useAuth>>) {
  vi.mocked(useAuth).mockReturnValue(value as unknown as ReturnType<typeof useAuth>)
}

describe('TopBar auth entry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuth({
      isAuthenticated: true,
      user: {
        id: 'u1',
        email: 'admin@example.com',
        name: '관리자',
        kind: 'human',
        mustChangePassword: false,
      },
    })
  })

  it('renders current user and logs out to /login', async () => {
    vi.mocked(api.logout).mockResolvedValue(undefined)

    render(<TopBar />, { wrapper: wrapper() })

    expect(screen.getByText('관리자')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(api.logout).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(push).toHaveBeenCalledWith('/login'))
  })
})
