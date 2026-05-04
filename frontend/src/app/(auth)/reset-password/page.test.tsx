import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * /reset-password — auth-password-policy P4.
 *
 * <p>token 존재(query token=t1) 가정에서 ADR #19 5규칙을 클라가 사전 차단하고
 * rule별 한국어 메시지를 노출. backend 호출이 일어나지 않는다.
 */

const replaceMock = vi.fn()
let searchParams = new URLSearchParams('token=t1')
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn(), back: vi.fn() }),
  useSearchParams: () => searchParams,
}))

const mutateAsyncMock = vi.fn()
vi.mock('@/hooks/usePasswordReset', () => ({
  usePasswordReset: () => ({
    mutateAsync: mutateAsyncMock,
    isPending: false,
  }),
}))

import ResetPasswordPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const fillBoth = (pw: string, confirmPw: string = pw) => {
  fireEvent.change(screen.getByLabelText(/^새 비밀번호 \(/), { target: { value: pw } })
  fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: confirmPw } })
}

describe('ResetPasswordPage — ADR #19 client validation (auth-password-policy P4)', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mutateAsyncMock.mockReset()
    searchParams = new URLSearchParams('token=t1')
  })

  it('11자 — min_length 메시지', async () => {
    wrap(<ResetPasswordPage />)
    fillBoth('abcdefghij1')
    fireEvent.click(screen.getByRole('button', { name: /^비밀번호 변경$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/12자 이상/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('숫자만 — missing_alpha 메시지', async () => {
    wrap(<ResetPasswordPage />)
    fillBoth('123456789012')
    fireEvent.click(screen.getByRole('button', { name: /^비밀번호 변경$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/영문자/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('영문만 — missing_digit 메시지', async () => {
    wrap(<ResetPasswordPage />)
    fillBoth('abcdefghijkl')
    fireEvent.click(screen.getByRole('button', { name: /^비밀번호 변경$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/숫자/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('공백 — whitespace 메시지', async () => {
    wrap(<ResetPasswordPage />)
    fillBoth('abcdef 12345')
    fireEvent.click(screen.getByRole('button', { name: /^비밀번호 변경$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/공백/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('확인 미일치 우선 (정책 통과여도 mismatch 메시지)', async () => {
    wrap(<ResetPasswordPage />)
    fillBoth('abcdefghijk1', 'abcdefghijk2')
    fireEvent.click(screen.getByRole('button', { name: /^비밀번호 변경$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/일치하지 않/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('정책 통과(12자 영+숫) — backend 호출됨', async () => {
    mutateAsyncMock.mockResolvedValue({ ok: true })
    wrap(<ResetPasswordPage />)
    fillBoth('abcdefghijk1')
    fireEvent.click(screen.getByRole('button', { name: /^비밀번호 변경$/ }))
    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        token: 't1',
        newPassword: 'abcdefghijk1',
      })
    })
  })
})
