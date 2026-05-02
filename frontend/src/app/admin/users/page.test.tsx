import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import AdminInviteUserPage from './page'
import { api } from '@/lib/api'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

vi.mock('@/lib/api', () => ({
  api: { adminInviteUser: vi.fn() },
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ back: vi.fn(), push: vi.fn(), replace: vi.fn() }),
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return render(<AdminInviteUserPage />, { wrapper: Wrapper })
}

/**
 * ADR #21 admin closure (P4) — `/admin/users` 페이지: form (email/displayName/role select)
 * + `useAdminInviteUser` 위임 + onSuccess(reset+안내) / onError(409=duplicate, 403=permission, etc).
 */
describe('AdminInviteUserPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('form 필드 렌더 (email / displayName / role select / submit)', () => {
    renderPage()
    expect(screen.getByLabelText(/이메일/)).not.toBeNull()
    expect(screen.getByLabelText(/이름/)).not.toBeNull()
    expect(screen.getByLabelText(/역할/)).not.toBeNull()
    expect(screen.getByRole('button', { name: /초대/ })).not.toBeNull()
  })

  it('role select 옵션은 MEMBER / AUDITOR / ADMIN', () => {
    renderPage()
    const select = screen.getByLabelText(/역할/) as HTMLSelectElement
    const values = Array.from(select.options).map((o) => o.value)
    expect(values).toEqual(expect.arrayContaining(['MEMBER', 'AUDITOR', 'ADMIN']))
  })

  it('성공 → 안내 메시지 + 입력 reset', async () => {
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'u-1',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
      mustChangePassword: true,
    })
    renderPage()

    const email = screen.getByLabelText(/이메일/) as HTMLInputElement
    const name = screen.getByLabelText(/이름/) as HTMLInputElement
    const role = screen.getByLabelText(/역할/) as HTMLSelectElement
    fireEvent.change(email, { target: { value: 'alice@example.com' } })
    fireEvent.change(name, { target: { value: 'Alice' } })
    fireEvent.change(role, { target: { value: 'MEMBER' } })
    fireEvent.click(screen.getByRole('button', { name: /초대/ }))

    await waitFor(() => {
      expect(screen.getByRole('status').textContent ?? '').toMatch(/alice@example.com/)
    })
    expect(api.adminInviteUser).toHaveBeenCalledWith({
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
    })
    // reset
    expect(email.value).toBe('')
    expect(name.value).toBe('')
  })

  it('409 DUPLICATE_EMAIL → "이미 가입된 이메일" 안내', async () => {
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 409,
      code: 'CONFLICT',
      reason: 'DUPLICATE_EMAIL',
    })
    renderPage()
    fireEvent.change(screen.getByLabelText(/이메일/), {
      target: { value: 'dup@example.com' },
    })
    fireEvent.change(screen.getByLabelText(/이름/), { target: { value: 'Dup' } })
    fireEvent.change(screen.getByLabelText(/역할/), { target: { value: 'MEMBER' } })
    fireEvent.click(screen.getByRole('button', { name: /초대/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent ?? '').toMatch(/이미 가입/)
    })
  })

  it('403 PERMISSION_DENIED → "권한이 없습니다" 안내', async () => {
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 403,
      code: 'PERMISSION_DENIED',
    })
    renderPage()
    fireEvent.change(screen.getByLabelText(/이메일/), {
      target: { value: 'a@example.com' },
    })
    fireEvent.change(screen.getByLabelText(/이름/), { target: { value: 'A' } })
    fireEvent.change(screen.getByLabelText(/역할/), { target: { value: 'MEMBER' } })
    fireEvent.click(screen.getByRole('button', { name: /초대/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent ?? '').toMatch(/권한/)
    })
  })

  it('기타 에러 → 일반 실패 안내', async () => {
    ;(api.adminInviteUser as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 500,
    })
    renderPage()
    fireEvent.change(screen.getByLabelText(/이메일/), {
      target: { value: 'a@example.com' },
    })
    fireEvent.change(screen.getByLabelText(/이름/), { target: { value: 'A' } })
    fireEvent.change(screen.getByLabelText(/역할/), { target: { value: 'MEMBER' } })
    fireEvent.click(screen.getByRole('button', { name: /초대/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent ?? '').toMatch(/실패/)
    })
  })
})
