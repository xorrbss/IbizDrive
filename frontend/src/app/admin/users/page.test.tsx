import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
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
vi.mock('@/hooks/useAdminUsers', () => ({
  useAdminUsers: () => usersQueryState,
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

  it('비활성 사용자 — 비활성화 버튼 disabled', () => {
    usersQueryState = {
      isLoading: false,
      isError: false,
      data: {
        ...PAGE_DATA,
        content: [{ ...PAGE_DATA.content[0], isActive: false }],
      },
    }
    wrap(<AdminUsersPage />)
    const btn = screen.getByLabelText('alice@example.com 비활성화') as HTMLButtonElement
    expect(btn.disabled).toBe(true)
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
