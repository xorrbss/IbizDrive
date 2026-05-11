import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AdminGrantPermissionTrigger } from './AdminGrantPermissionTrigger'

// GrantPermissionDialog는 별도 책임 — props 가시화용 stub.
vi.mock('@/components/files/GrantPermissionDialog', () => ({
  GrantPermissionDialog: (props: {
    resource: 'folder' | 'file'
    resourceId: string
    open: boolean
    onClose: () => void
    onSuccess?: () => void
  }) =>
    props.open ? (
      <div
        data-testid="grant-dialog-stub"
        data-resource={props.resource}
        data-resource-id={props.resourceId}
      >
        <button onClick={props.onClose}>stub-close</button>
        <button onClick={() => props.onSuccess?.()}>stub-success</button>
      </div>
    ) : null,
}))

const VALID_UUID = '11111111-1111-1111-1111-111111111111'

describe('AdminGrantPermissionTrigger', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('+ 권한 부여 버튼 렌더 + 초기 picker 미노출', () => {
    render(<AdminGrantPermissionTrigger />)
    expect(screen.getByRole('button', { name: '+ 권한 부여' })).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('+ 권한 부여 클릭 → ResourcePicker 모달 + radio 2종 + UUID input 노출', () => {
    render(<AdminGrantPermissionTrigger />)
    fireEvent.click(screen.getByRole('button', { name: '+ 권한 부여' }))
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByRole('radio', { name: '폴더' })).toBeTruthy()
    expect(screen.getByRole('radio', { name: '파일' })).toBeTruthy()
    expect(screen.getByLabelText('리소스 UUID')).toBeTruthy()
    // default — folder radio 체크
    expect(
      (screen.getByRole('radio', { name: '폴더' }) as HTMLInputElement).checked,
    ).toBe(true)
  })

  it('UUID 형식 invalid → alert 노출 + GrantPermissionDialog 미노출', () => {
    render(<AdminGrantPermissionTrigger />)
    fireEvent.click(screen.getByRole('button', { name: '+ 권한 부여' }))
    const input = screen.getByLabelText('리소스 UUID') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'not-a-uuid' } })
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    expect(screen.getByRole('alert').textContent).toMatch(/UUID 형식/)
    expect(screen.queryByTestId('grant-dialog-stub')).toBeNull()
  })

  it('유효한 UUID + folder radio + 다음 → GrantPermissionDialog 모달 노출 (props 전달)', () => {
    render(<AdminGrantPermissionTrigger />)
    fireEvent.click(screen.getByRole('button', { name: '+ 권한 부여' }))
    fireEvent.change(screen.getByLabelText('리소스 UUID'), {
      target: { value: VALID_UUID },
    })
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    const dialog = screen.getByTestId('grant-dialog-stub')
    expect(dialog.getAttribute('data-resource')).toBe('folder')
    expect(dialog.getAttribute('data-resource-id')).toBe(VALID_UUID)
  })

  it('file radio + 다음 → GrantPermissionDialog의 resource=file', () => {
    render(<AdminGrantPermissionTrigger />)
    fireEvent.click(screen.getByRole('button', { name: '+ 권한 부여' }))
    fireEvent.click(screen.getByRole('radio', { name: '파일' }))
    fireEvent.change(screen.getByLabelText('리소스 UUID'), {
      target: { value: VALID_UUID },
    })
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    const dialog = screen.getByTestId('grant-dialog-stub')
    expect(dialog.getAttribute('data-resource')).toBe('file')
  })

  it('picker 단계 — 취소 클릭 시 모달 닫힘', () => {
    render(<AdminGrantPermissionTrigger />)
    fireEvent.click(screen.getByRole('button', { name: '+ 권한 부여' }))
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('GrantPermissionDialog onClose/onSuccess → 닫기', () => {
    render(<AdminGrantPermissionTrigger />)
    fireEvent.click(screen.getByRole('button', { name: '+ 권한 부여' }))
    fireEvent.change(screen.getByLabelText('리소스 UUID'), {
      target: { value: VALID_UUID },
    })
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    expect(screen.getByTestId('grant-dialog-stub')).toBeTruthy()
    fireEvent.click(screen.getByText('stub-success'))
    expect(screen.queryByTestId('grant-dialog-stub')).toBeNull()
  })

  it('picker Esc 키 → 모달 닫힘', () => {
    render(<AdminGrantPermissionTrigger />)
    fireEvent.click(screen.getByRole('button', { name: '+ 권한 부여' }))
    const dialog = screen.getByRole('dialog')
    fireEvent.keyDown(dialog, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
