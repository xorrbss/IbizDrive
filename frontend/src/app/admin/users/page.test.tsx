import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AdminUserPage } from '@/lib/api'

/**
 * /admin/users — 초대 폼 + 사용자 목록 (admin-user-mgmt).
 *
 * <p>초대 섹션은 m-admin-entry-rewrite P8에서 검증한 매트릭스 보존. 추가로 admin-user-mgmt
 * 트랙의 list/role-change/deactivate UX를 검증한다. self-protection은 backend 진실 — 본 테스트는
 * 403 응답이 인라인 에러로 노출되는지만 가드.
 *
 * <p>임시 PW는 어떤 응답에도 부재 (docs/03 §2.8) — 회귀 가드 보존.
 */

const inviteMutateAsyncMock = vi.fn()
const inviteIsPendingRef = { current: false }
vi.mock('@/hooks/useAdminInviteUser', () => ({
  useAdminInviteUser: () => ({
    mutateAsync: inviteMutateAsyncMock,
    get isPending() {
      return inviteIsPendingRef.current
    },
  }),
}))

let usersQueryState: {
  data?: AdminUserPage
  isLoading: boolean
  isError: boolean
} = { isLoading: false, isError: false }
const useAdminUsersMock: ReturnType<typeof vi.fn> = vi.fn(() => usersQueryState)
vi.mock('@/hooks/useAdminUsers', () => ({
  useAdminUsers: (page?: number, size?: number, q?: string) =>
    useAdminUsersMock(page, size, q),
}))

const updateMutateAsyncMock = vi.fn()
const updateIsPendingRef = { current: false }
vi.mock('@/hooks/useAdminUpdateUser', () => ({
  useAdminUpdateUser: () => ({
    mutateAsync: updateMutateAsyncMock,
    get isPending() {
      return updateIsPendingRef.current
    },
  }),
}))

// AdminGuard 격리 — wave1.5-auditor-admin-ui-access로 페이지가 default
// `<AdminGuard>` 안쪽에 들어갔으므로 children이 렌더되도록 ADMIN role mock.
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))
vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({
    data: {
      user: { id: 'u1', email: 'a@b.com', name: 'A', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'k',
    },
    isLoading: false,
    isError: false,
  }),
}))

import AdminUsersPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const PAGE_DATA: AdminUserPage = {
  content: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'ADMIN',
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
      lastLoginAt: null,
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      email: 'bob@example.com',
      displayName: 'Bob',
      role: 'MEMBER',
      isActive: true,
      createdAt: '2026-01-02T00:00:00Z',
      lastLoginAt: null,
    },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 50,
}

describe('AdminUsersPage — invite form (m-admin-entry-rewrite P8 회귀)', () => {
  beforeEach(() => {
    inviteMutateAsyncMock.mockReset()
    inviteIsPendingRef.current = false
    updateMutateAsyncMock.mockReset()
    updateIsPendingRef.current = false
    usersQueryState = { isLoading: false, isError: false, data: PAGE_DATA }
  })

  it('초기 렌더 — email/displayName/role 필드 + 초대 버튼 표시', () => {
    wrap(<AdminUsersPage />)
    expect(screen.getByLabelText('이메일')).toBeTruthy()
    expect(screen.getByLabelText('표시 이름')).toBeTruthy()
    // 역할 select가 invite + 각 row 마다 존재하므로 정확 매칭은 invite 섹션 first 매칭으로
    expect(screen.getAllByLabelText('역할').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /^초대$/ })).toBeTruthy()
  })

  it('성공 — useAdminInviteUser 호출 + 안내 메시지 + 폼 리셋', async () => {
    inviteMutateAsyncMock.mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'bob@example.com',
      displayName: 'Bob',
      role: 'MEMBER',
      mustChangePassword: true,
    })
    wrap(<AdminUsersPage />)

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'bob@example.com' },
    })
    fireEvent.change(screen.getByLabelText('표시 이름'), {
      target: { value: 'Bob' },
    })
    fireEvent.change(screen.getByLabelText('역할'), {
      target: { value: 'MEMBER' },
    })
    fireEvent.click(screen.getByRole('button', { name: /^초대$/ }))

    await waitFor(() => {
      expect(inviteMutateAsyncMock).toHaveBeenCalledWith({
        email: 'bob@example.com',
        displayName: 'Bob',
        role: 'MEMBER',
      })
    })
    await waitFor(() => {
      expect(screen.getByRole('status').textContent ?? '').toMatch(/초대 메일/)
    })
    expect((screen.getByLabelText('이메일') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('표시 이름') as HTMLInputElement).value).toBe('')
  })

  it('409 DUPLICATE_EMAIL — 인라인 에러 메시지 표시', async () => {
    const err = Object.assign(new Error('adminInviteUser failed: 409'), {
      status: 409,
      code: 'CONFLICT',
      reason: 'DUPLICATE_EMAIL',
    })
    inviteMutateAsyncMock.mockRejectedValue(err)
    wrap(<AdminUsersPage />)

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'dup@example.com' },
    })
    fireEvent.change(screen.getByLabelText('표시 이름'), {
      target: { value: 'Dup' },
    })
    fireEvent.change(screen.getByLabelText('역할'), {
      target: { value: 'MEMBER' },
    })
    fireEvent.click(screen.getByRole('button', { name: /^초대$/ }))

    await waitFor(() => {
      const alerts = screen.getAllByRole('alert')
      expect(alerts.some((a) => /이미.*이메일|중복/.test(a.textContent ?? ''))).toBe(true)
    })
  })

  it('성공 응답에 임시 PW 노출 금지 — DOM에 password/임시 단어 부재 (회귀 가드)', async () => {
    inviteMutateAsyncMock.mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'bob@example.com',
      displayName: 'Bob',
      role: 'MEMBER',
      mustChangePassword: true,
    })
    wrap(<AdminUsersPage />)

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'bob@example.com' },
    })
    fireEvent.change(screen.getByLabelText('표시 이름'), {
      target: { value: 'Bob' },
    })
    fireEvent.change(screen.getByLabelText('역할'), {
      target: { value: 'MEMBER' },
    })
    fireEvent.click(screen.getByRole('button', { name: /^초대$/ }))

    await waitFor(() => {
      expect(screen.getByRole('status')).toBeTruthy()
    })
    const status = screen.getByRole('status').textContent ?? ''
    expect(status).not.toMatch(/임시\s*비밀번호|tempPassword|password\s*=/i)
  })
})

describe('AdminUsersPage — list section (admin-user-mgmt)', () => {
  beforeEach(() => {
    inviteMutateAsyncMock.mockReset()
    inviteIsPendingRef.current = false
    updateMutateAsyncMock.mockReset()
    updateIsPendingRef.current = false
    usersQueryState = { isLoading: false, isError: false, data: PAGE_DATA }
  })

  it('목록 렌더 — 사용자 행이 표시되고 각 행에 비활성화 버튼이 있다', () => {
    wrap(<AdminUsersPage />)
    expect(screen.getByText('alice@example.com')).toBeTruthy()
    expect(screen.getByText('bob@example.com')).toBeTruthy()
    expect(screen.getByLabelText('alice@example.com 비활성화')).toBeTruthy()
    expect(screen.getByLabelText('bob@example.com 비활성화')).toBeTruthy()
  })

  it('역할 변경 — useAdminUpdateUser({ id, body: { role } }) 호출', async () => {
    updateMutateAsyncMock.mockResolvedValue({})
    wrap(<AdminUsersPage />)

    fireEvent.change(screen.getByLabelText('bob@example.com 역할'), {
      target: { value: 'AUDITOR' },
    })

    await waitFor(() => {
      expect(updateMutateAsyncMock).toHaveBeenCalledWith({
        id: '22222222-2222-2222-2222-222222222222',
        body: { role: 'AUDITOR' },
      })
    })
  })

  it('비활성화 — useAdminUpdateUser({ id, body: { isActive: false } }) 호출', async () => {
    updateMutateAsyncMock.mockResolvedValue({})
    wrap(<AdminUsersPage />)

    fireEvent.click(screen.getByLabelText('bob@example.com 비활성화'))

    await waitFor(() => {
      expect(updateMutateAsyncMock).toHaveBeenCalledWith({
        id: '22222222-2222-2222-2222-222222222222',
        body: { isActive: false },
      })
    })
  })

  it('403 SELF_PROTECTION — 인라인 에러 표시 ("본인은 변경할 수 없습니다")', async () => {
    const err = Object.assign(new Error('adminUpdateUser failed: 403'), {
      status: 403,
      code: 'FORBIDDEN',
      reason: 'SELF_PROTECTION',
    })
    updateMutateAsyncMock.mockRejectedValue(err)
    wrap(<AdminUsersPage />)

    fireEvent.click(screen.getByLabelText('alice@example.com 비활성화'))

    await waitFor(() => {
      const alerts = screen.getAllByRole('alert')
      expect(alerts.some((a) => /본인은 변경할 수 없습니다/.test(a.textContent ?? ''))).toBe(true)
    })
  })

  it('비활성 사용자 — 비활성화 버튼 대신 재활성화 버튼 노출 (admin-user-search-update)', () => {
    usersQueryState = {
      isLoading: false,
      isError: false,
      data: {
        ...PAGE_DATA,
        content: [{ ...PAGE_DATA.content[0], isActive: false }],
      },
    }
    wrap(<AdminUsersPage />)
    expect(screen.queryByLabelText('alice@example.com 비활성화')).toBeNull()
    expect(screen.getByLabelText('alice@example.com 재활성화')).toBeTruthy()
  })

  it('빈 목록 — 안내 메시지 표시', () => {
    usersQueryState = {
      isLoading: false,
      isError: false,
      data: { ...PAGE_DATA, content: [], totalElements: 0 },
    }
    wrap(<AdminUsersPage />)
    expect(screen.getByText('사용자가 없습니다.')).toBeTruthy()
  })

  it('로딩 상태 — "불러오는 중…" 표시', () => {
    usersQueryState = { isLoading: true, isError: false }
    wrap(<AdminUsersPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('에러 상태 — alert 노출', () => {
    usersQueryState = { isLoading: false, isError: true }
    wrap(<AdminUsersPage />)
    const alerts = screen.getAllByRole('alert')
    expect(alerts.some((a) => /목록을 불러오지 못했습니다/.test(a.textContent ?? ''))).toBe(true)
  })
})

describe('AdminUsersPage — admin-user-search-update (Wave 1 — T1)', () => {
  beforeEach(() => {
    inviteMutateAsyncMock.mockReset()
    inviteIsPendingRef.current = false
    updateMutateAsyncMock.mockReset()
    updateIsPendingRef.current = false
    useAdminUsersMock.mockClear()
    usersQueryState = { isLoading: false, isError: false, data: PAGE_DATA }
  })

  it('초기 렌더 — useAdminUsers(0, 50, "") 호출', () => {
    wrap(<AdminUsersPage />)
    expect(useAdminUsersMock).toHaveBeenCalledWith(0, 50, '')
  })

  it('검색 입력 — debounce 후 useAdminUsers에 q 전달', async () => {
    wrap(<AdminUsersPage />)
    fireEvent.change(screen.getByLabelText('사용자 검색'), {
      target: { value: 'alice' },
    })
    // debounce 300ms — useDebounce는 real setTimeout이므로 실제 대기 후 act로 플러시.
    await act(async () => {
      await new Promise((r) => setTimeout(r, 350))
    })
    await waitFor(() => {
      const calls = useAdminUsersMock.mock.calls
      expect(calls[calls.length - 1]).toEqual([0, 50, 'alice'])
    })
  })

  it('재활성화 — useAdminUpdateUser({ id, body: { isActive: true } }) 호출', async () => {
    usersQueryState = {
      isLoading: false,
      isError: false,
      data: {
        ...PAGE_DATA,
        content: [{ ...PAGE_DATA.content[0], isActive: false }],
      },
    }
    updateMutateAsyncMock.mockResolvedValue({})
    wrap(<AdminUsersPage />)

    fireEvent.click(screen.getByLabelText('alice@example.com 재활성화'))

    await waitFor(() => {
      expect(updateMutateAsyncMock).toHaveBeenCalledWith({
        id: '11111111-1111-1111-1111-111111111111',
        body: { isActive: true },
      })
    })
  })

  it('표시 이름 편집 — 편집 진입 + 저장 → mutate({ displayName })', async () => {
    updateMutateAsyncMock.mockResolvedValue({})
    wrap(<AdminUsersPage />)

    fireEvent.click(screen.getByLabelText('alice@example.com 표시 이름 편집'))
    const input = screen.getByLabelText('alice@example.com 표시 이름 편집') as HTMLInputElement
    fireEvent.change(input, { target: { value: '  Alice Updated  ' } })
    fireEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => {
      expect(updateMutateAsyncMock).toHaveBeenCalledWith({
        id: '11111111-1111-1111-1111-111111111111',
        body: { displayName: 'Alice Updated' },
      })
    })
  })

  it('표시 이름 편집 — blank 입력 시 mutate 호출 없이 인라인 에러', async () => {
    wrap(<AdminUsersPage />)
    fireEvent.click(screen.getByLabelText('alice@example.com 표시 이름 편집'))
    const input = screen.getByLabelText('alice@example.com 표시 이름 편집') as HTMLInputElement
    fireEvent.change(input, { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(updateMutateAsyncMock).not.toHaveBeenCalled()
    const alerts = screen.getAllByRole('alert')
    expect(alerts.some((a) => /1-100자/.test(a.textContent ?? ''))).toBe(true)
  })

  it('표시 이름 편집 — 같은 값 입력 시 mutate 호출 없이 편집 모드 종료', async () => {
    wrap(<AdminUsersPage />)
    fireEvent.click(screen.getByLabelText('alice@example.com 표시 이름 편집'))
    fireEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(updateMutateAsyncMock).not.toHaveBeenCalled()
    // 편집 종료 → 편집 input은 사라지고 편집 버튼이 다시 노출
    expect(screen.getByLabelText('alice@example.com 표시 이름 편집').tagName).toBe('BUTTON')
  })

  it('표시 이름 편집 — 취소 시 mutate 호출 없음', () => {
    wrap(<AdminUsersPage />)
    fireEvent.click(screen.getByLabelText('alice@example.com 표시 이름 편집'))
    const input = screen.getByLabelText('alice@example.com 표시 이름 편집') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'Different' } })
    fireEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(updateMutateAsyncMock).not.toHaveBeenCalled()
  })
})
