import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * /account/password — auth-must-change-pw P4.
 *
 * <p>force=1 query면 강제 모드 — 배너 표시 + "돌아가기" 숨김 + 변경 성공 후
 * 자동 /files redirect (사용자가 force redirect 루프에 머무르지 않도록).
 */

const replaceMock = vi.fn()
const backMock = vi.fn()
let searchParams = new URLSearchParams()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, back: backMock, push: vi.fn() }),
  useSearchParams: () => searchParams,
}))

const mutateAsyncMock = vi.fn()
const isPendingRef = { current: false }
vi.mock('@/hooks/usePasswordChange', () => ({
  usePasswordChange: () => ({
    mutateAsync: mutateAsyncMock,
    get isPending() { return isPendingRef.current },
  }),
}))

import ChangePasswordPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('ChangePasswordPage — force mode (auth-must-change-pw P4)', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    backMock.mockReset()
    mutateAsyncMock.mockReset()
    isPendingRef.current = false
    searchParams = new URLSearchParams()
  })

  it('?force=1 시 강제 모드 배너 표시', () => {
    searchParams = new URLSearchParams('force=1')
    wrap(<ChangePasswordPage />)
    expect(screen.getByRole('alert', { name: /비밀번호 변경 강제/ })).toBeTruthy()
  })

  it('?force=1 시 "돌아가기" 버튼 숨김', () => {
    searchParams = new URLSearchParams('force=1')
    wrap(<ChangePasswordPage />)
    expect(screen.queryByRole('button', { name: /돌아가기/ })).toBeNull()
  })

  it('일반 모드(force 없음)에서는 배너 없음 + 돌아가기 표시', () => {
    wrap(<ChangePasswordPage />)
    expect(screen.queryByRole('alert', { name: /비밀번호 변경 강제/ })).toBeNull()
    expect(screen.getByRole('button', { name: /돌아가기/ })).toBeTruthy()
  })

  it('force 모드에서 변경 성공 시 router.replace("/files")', async () => {
    searchParams = new URLSearchParams('force=1')
    mutateAsyncMock.mockResolvedValue({ message: 'ok' })
    wrap(<ChangePasswordPage />)

    fireEvent.change(screen.getByLabelText('현재 비밀번호'), { target: { value: 'oldOldOld!' } })
    fireEvent.change(screen.getByLabelText('새 비밀번호 (8자 이상)'), { target: { value: 'NewSecret456!' } })
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'NewSecret456!' } })
    fireEvent.click(screen.getByRole('button', { name: /^변경$/ }))

    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith('/files')
    })
  })

  it('일반 모드 변경 성공 시 자동 redirect 없음 (사용자가 머물러 있음)', async () => {
    mutateAsyncMock.mockResolvedValue({ message: 'ok' })
    wrap(<ChangePasswordPage />)

    fireEvent.change(screen.getByLabelText('현재 비밀번호'), { target: { value: 'oldOldOld!' } })
    fireEvent.change(screen.getByLabelText('새 비밀번호 (8자 이상)'), { target: { value: 'NewSecret456!' } })
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'NewSecret456!' } })
    fireEvent.click(screen.getByRole('button', { name: /^변경$/ }))

    await waitFor(() => {
      expect(mutateAsyncMock).toHaveBeenCalled()
    })
    // 짧은 대기 후에도 replace 호출 없음
    await new Promise((r) => setTimeout(r, 20))
    expect(replaceMock).not.toHaveBeenCalled()
  })
})
