import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { RetentionPolicyEditor } from './RetentionPolicyEditor'
import * as api from '@/lib/api'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    updateAdminTrashPolicy: vi.fn(),
  }
})

function wrap() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

function renderEditor(currentDays = 30) {
  return render(<RetentionPolicyEditor currentDays={currentDays} />, { wrapper: wrap() })
}

describe('RetentionPolicyEditor (Phase C)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
  })

  it('초기 렌더 — input value=currentDays + 변경 버튼 disabled (unchanged)', () => {
    renderEditor(30)
    const input = screen.getByLabelText('새 보존 일수') as HTMLInputElement
    expect(input.value).toBe('30')
    const submit = screen.getByRole('button', { name: '정책 변경' })
    expect((submit as HTMLButtonElement).disabled).toBe(true)
  })

  it('값 변경 → 변경 버튼 활성화 + diff 미리보기 노출', () => {
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '14' } })
    expect((screen.getByRole('button', { name: '정책 변경' }) as HTMLButtonElement).disabled).toBe(false)
    // diff 미리보기 — "30일 → 14일" 형식의 단일 paragraph (input value도 14라 multi-match 회피용 getAllByText)
    expect(screen.getAllByText(/30/).length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText(/14/).length).toBeGreaterThanOrEqual(1)
  })

  it('감소 시 인라인 경고 노출 ("기존 휴지통 항목은 영향받지 않습니다")', () => {
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '14' } })
    const alert = screen.getByLabelText('일수 감소 경고')
    expect(alert.textContent).toMatch(/기존 휴지통 항목은 영향받지 않습니다/)
  })

  it('증가 시 감소 경고 미노출', () => {
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '60' } })
    expect(screen.queryByLabelText('일수 감소 경고')).toBeNull()
  })

  it('범위 위반(7~90 밖) → 변경 버튼 disabled + 범위 경고 노출', () => {
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '6' } })
    expect((screen.getByRole('button', { name: '정책 변경' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/7일 ~ 90일 범위 안에서 입력해 주세요/)).toBeTruthy()

    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '91' } })
    expect((screen.getByRole('button', { name: '정책 변경' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('정책 변경 클릭 → ConfirmDialog 노출 + 단일-approver 명시', () => {
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '14' } })
    fireEvent.click(screen.getByRole('button', { name: '정책 변경' }))

    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeTruthy()
    expect(dialog.textContent).toMatch(/30/)
    expect(dialog.textContent).toMatch(/14/)
    expect(dialog.textContent).toMatch(/단일 ADMIN 즉시 적용/)
  })

  it('Confirm 변경 → api 호출 + 성공 시 toast.success + dialog 닫힘', async () => {
    ;(api.updateAdminTrashPolicy as ReturnType<typeof vi.fn>).mockResolvedValue({ retentionDays: 14 })
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '14' } })
    fireEvent.click(screen.getByRole('button', { name: '정책 변경' }))
    fireEvent.click(screen.getByRole('button', { name: '변경' }))

    await waitFor(() => expect(api.updateAdminTrashPolicy).toHaveBeenCalledWith(14))
    await waitFor(() => expect(toastSpy('success')).toHaveBeenCalledWith('보존 일수를 14일로 변경했습니다'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('ConfirmDialog 취소 → dialog 닫힘 + api 미호출', () => {
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '14' } })
    fireEvent.click(screen.getByRole('button', { name: '정책 변경' }))
    fireEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(api.updateAdminTrashPolicy).not.toHaveBeenCalled()
  })

  it('400 VALIDATION_ERROR → dialog 내 inline alert (dialog 유지)', async () => {
    const apiError = Object.assign(new Error('updateAdminTrashPolicy failed: 400'), {
      status: 400,
      code: 'VALIDATION_ERROR',
    })
    ;(api.updateAdminTrashPolicy as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '14' } })
    fireEvent.click(screen.getByRole('button', { name: '정책 변경' }))
    fireEvent.click(screen.getByRole('button', { name: '변경' }))

    await waitFor(() => {
      const dialog = screen.getByRole('dialog')
      expect(dialog.textContent).toMatch(/입력값이 올바르지 않습니다/)
    })
  })

  it('403 PERMISSION_DENIED → toast.error + dialog 닫힘', async () => {
    const apiError = Object.assign(new Error('updateAdminTrashPolicy failed: 403'), {
      status: 403,
      code: 'PERMISSION_DENIED',
    })
    ;(api.updateAdminTrashPolicy as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    renderEditor(30)
    fireEvent.change(screen.getByLabelText('새 보존 일수'), { target: { value: '14' } })
    fireEvent.click(screen.getByRole('button', { name: '정책 변경' }))
    fireEvent.click(screen.getByRole('button', { name: '변경' }))

    await waitFor(() => expect(toastSpy('error')).toHaveBeenCalledWith('보존 정책 변경 권한이 없습니다'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })
})
