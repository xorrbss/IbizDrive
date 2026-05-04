import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * /signup — auth-password-policy P4.
 *
 * <p>ADR #19 5규칙 — 클라 사전검증이 backend `PasswordPolicyValidator`와 동일 매트릭스로
 * 거부하고 rule별 한국어 메시지를 노출. backend 호출은 일어나지 않는다(클라가 사전 차단).
 */

const replaceMock = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn(), back: vi.fn() }),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

const mutateAsyncMock = vi.fn()
vi.mock('@/hooks/useSignup', () => ({
  useSignup: () => ({
    mutateAsync: mutateAsyncMock,
    isPending: false,
  }),
}))

import SignupPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const fillBasics = () => {
  fireEvent.change(screen.getByLabelText('이름'), { target: { value: 'Alice' } })
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'a@b.com' } })
}

describe('SignupPage — ADR #19 client validation (auth-password-policy P4)', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mutateAsyncMock.mockReset()
    useMeMock.mockReset()
    useMeMock.mockReturnValue({ data: null, isLoading: false, isError: false })
  })

  it('11자(영+숫) — min_length 메시지', async () => {
    wrap(<SignupPage />)
    fillBasics()
    fireEvent.change(screen.getByLabelText(/비밀번호/), { target: { value: 'abcdefghij1' } })
    fireEvent.click(screen.getByRole('button', { name: /^회원가입$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/12자 이상/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('129자 — max_length 메시지', async () => {
    wrap(<SignupPage />)
    fillBasics()
    const pw = 'a'.repeat(64) + '1'.repeat(65)
    fireEvent.change(screen.getByLabelText(/비밀번호/), { target: { value: pw } })
    fireEvent.click(screen.getByRole('button', { name: /^회원가입$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/128자 이하/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('숫자만 12자 — missing_alpha 메시지', async () => {
    wrap(<SignupPage />)
    fillBasics()
    fireEvent.change(screen.getByLabelText(/비밀번호/), { target: { value: '123456789012' } })
    fireEvent.click(screen.getByRole('button', { name: /^회원가입$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/영문자/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('영문만 12자 — missing_digit 메시지', async () => {
    wrap(<SignupPage />)
    fillBasics()
    fireEvent.change(screen.getByLabelText(/비밀번호/), { target: { value: 'abcdefghijkl' } })
    fireEvent.click(screen.getByRole('button', { name: /^회원가입$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/숫자/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('공백 포함 — whitespace 메시지', async () => {
    wrap(<SignupPage />)
    fillBasics()
    fireEvent.change(screen.getByLabelText(/비밀번호/), { target: { value: 'abcdef 12345' } })
    fireEvent.click(screen.getByRole('button', { name: /^회원가입$/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/공백/)
    })
    expect(mutateAsyncMock).not.toHaveBeenCalled()
  })

  it('정책 통과(12자 영+숫) — backend 호출됨', async () => {
    mutateAsyncMock.mockResolvedValue({ ok: true })
    wrap(<SignupPage />)
    fillBasics()
    fireEvent.change(screen.getByLabelText(/비밀번호/), { target: { value: 'abcdefghijk1' } })
    fireEvent.click(screen.getByRole('button', { name: /^회원가입$/ }))
    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        email: 'a@b.com',
        password: 'abcdefghijk1',
        displayName: 'Alice',
      })
    })
  })
})
