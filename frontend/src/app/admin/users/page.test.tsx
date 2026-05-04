import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * /admin/users — m-admin-entry-rewrite P8.
 *
 * <p>관리자 사용자 초대 폼 — email + displayName + role(MEMBER/AUDITOR/ADMIN).
 * 성공 시 안내 메시지 + 폼 리셋. 409 DUPLICATE_EMAIL은 인라인 에러로 노출.
 *
 * <p>임시 PW는 응답에 부재 — UI에서 노출/표시하지 않음 (docs/03 §2.8).
 */

const mutateAsyncMock = vi.fn()
const isPendingRef = { current: false }
vi.mock('@/hooks/useAdminInviteUser', () => ({
  useAdminInviteUser: () => ({
    mutateAsync: mutateAsyncMock,
    get isPending() {
      return isPendingRef.current
    },
  }),
}))

import AdminUsersPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('AdminUsersPage — invite form (m-admin-entry-rewrite P8)', () => {
  beforeEach(() => {
    mutateAsyncMock.mockReset()
    isPendingRef.current = false
  })

  it('초기 렌더 — email/displayName/role 필드 + 초대 버튼 표시', () => {
    wrap(<AdminUsersPage />)
    expect(screen.getByLabelText('이메일')).toBeTruthy()
    expect(screen.getByLabelText('표시 이름')).toBeTruthy()
    expect(screen.getByLabelText('역할')).toBeTruthy()
    expect(screen.getByRole('button', { name: /초대/ })).toBeTruthy()
  })

  it('성공 — useAdminInviteUser 호출 + 안내 메시지 + 폼 리셋', async () => {
    mutateAsyncMock.mockResolvedValue({
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
    fireEvent.click(screen.getByRole('button', { name: /초대/ }))

    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        email: 'bob@example.com',
        displayName: 'Bob',
        role: 'MEMBER',
      })
    })
    await waitFor(() => {
      expect(screen.getByRole('status').textContent ?? '').toMatch(/초대 메일/)
    })
    // 폼 리셋
    expect((screen.getByLabelText('이메일') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('표시 이름') as HTMLInputElement).value).toBe('')
  })

  it('409 DUPLICATE_EMAIL — 인라인 에러 메시지 표시', async () => {
    const err = Object.assign(new Error('adminInviteUser failed: 409'), {
      status: 409,
      code: 'CONFLICT',
      reason: 'DUPLICATE_EMAIL',
    })
    mutateAsyncMock.mockRejectedValue(err)
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
    fireEvent.click(screen.getByRole('button', { name: /초대/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent ?? '').toMatch(/이미.*이메일|중복/)
    })
  })

  it('성공 응답에 임시 PW 노출 금지 — DOM에 password/임시 단어 부재 (회귀 가드)', async () => {
    mutateAsyncMock.mockResolvedValue({
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
    fireEvent.click(screen.getByRole('button', { name: /초대/ }))

    await waitFor(() => {
      expect(screen.getByRole('status')).toBeTruthy()
    })
    // status 메시지 본문에 "임시 비밀번호" 또는 "tempPassword" 단어 부재
    const status = screen.getByRole('status').textContent ?? ''
    expect(status).not.toMatch(/임시\s*비밀번호|tempPassword|password\s*=/i)
  })
})
