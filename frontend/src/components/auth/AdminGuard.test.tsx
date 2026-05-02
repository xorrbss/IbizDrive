import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render } from '@testing-library/react'
import { AdminGuard } from './AdminGuard'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
}))

const meState: { data: unknown; isLoading: boolean; isError: boolean } = {
  data: undefined,
  isLoading: true,
  isError: false,
}
vi.mock('@/hooks/useMe', () => ({
  useMe: () => meState,
}))

describe('AdminGuard', () => {
  beforeEach(() => {
    replace.mockReset()
    meState.data = undefined
    meState.isLoading = true
    meState.isError = false
  })

  it('로딩 중에는 children/redirect 모두 발생하지 않음', () => {
    meState.isLoading = true
    meState.data = undefined
    const { queryByText } = render(<AdminGuard>secret</AdminGuard>)
    expect(queryByText('secret')).toBeNull()
    expect(replace).not.toHaveBeenCalled()
  })

  it('비-ADMIN 사용자는 /files로 redirect되며 children 미렌더', () => {
    meState.isLoading = false
    meState.data = {
      user: { id: 'u1', email: 'm@x.com', name: 'M', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: ['MEMBER'],
      effectivePermissionsCacheKey: 'k',
    }
    const { queryByText } = render(<AdminGuard>secret</AdminGuard>)
    expect(replace).toHaveBeenCalledWith('/files')
    expect(queryByText('secret')).toBeNull()
  })

  it('ADMIN 사용자는 children 렌더, redirect 미발생', () => {
    meState.isLoading = false
    meState.data = {
      user: { id: 'u1', email: 'a@x.com', name: 'A', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'k',
    }
    const { queryByText } = render(<AdminGuard>secret</AdminGuard>)
    expect(queryByText('secret')).not.toBeNull()
    expect(replace).not.toHaveBeenCalled()
  })
})
