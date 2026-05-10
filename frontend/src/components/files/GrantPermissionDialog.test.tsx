import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { GrantPermissionDialog } from './GrantPermissionDialog'
import { api } from '@/lib/api'
import { useUserSearch } from '@/hooks/useUserSearch'
import { useDepartmentSearch } from '@/hooks/useDepartmentSearch'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type { PermissionListItem } from '@/types/permission'
import type { UserSummary } from '@/types/user'
import type { DepartmentSummary } from '@/types/department'

vi.mock('@/lib/api', () => ({
  api: { grantPermission: vi.fn() },
}))
vi.mock('@/hooks/useUserSearch', () => ({ useUserSearch: vi.fn() }))
vi.mock('@/hooks/useDepartmentSearch', () => ({ useDepartmentSearch: vi.fn() }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

function setUserSearch(items: UserSummary[]) {
  ;(useUserSearch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: { items },
    isLoading: false,
    isFetching: false,
  })
}

function setDeptSearch(items: DepartmentSummary[]) {
  ;(useDepartmentSearch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: { items },
    isLoading: false,
    isFetching: false,
  })
}

function renderDialog(overrides?: { onClose?: () => void; onSuccess?: (g: PermissionListItem) => void }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = overrides?.onClose ?? vi.fn()
  const onSuccess = overrides?.onSuccess ?? vi.fn()
  const utils = render(
    <GrantPermissionDialog
      resource="folder"
      resourceId="fld_a"
      open
      onClose={onClose}
      onSuccess={onSuccess}
    />,
    { wrapper: wrap(qc) },
  )
  return { ...utils, onClose, onSuccess }
}

const PERMISSION: PermissionListItem = {
  id: 'perm-1',
  resourceType: 'folder',
  resourceId: 'fld_a',
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
  grantedBy: 'me',
  expiresAt: null,
  createdAt: '2026-05-10T00:00:00Z',
  subjectName: null,
}

const ALICE: UserSummary = {
  id: 'usr-alice',
  displayName: 'Alice',
  email: 'alice@ex.com',
}

const ENG: DepartmentSummary = { id: 'dep-eng', name: 'Engineering' }

function submit() {
  fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)
}

describe('GrantPermissionDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
    setUserSearch([])
    setDeptSearch([])
  })

  // ─────────────────────────── Phase B 회귀 가드 ───────────────────────────

  it('open=false일 때 미렌더', () => {
    const qc = new QueryClient()
    const { container } = render(
      <GrantPermissionDialog
        resource="folder"
        resourceId="fld_a"
        open={false}
        onClose={vi.fn()}
      />,
      { wrapper: wrap(qc) },
    )
    expect(container.firstChild).toBeNull()
  })

  it('preset 5값 모두 노출 + 3 subject 라디오 (Phase C)', () => {
    renderDialog()
    expect(screen.getByRole('radio', { name: '모든 사용자' })).toBeTruthy()
    expect(screen.getByRole('radio', { name: '특정 사용자' })).toBeTruthy()
    expect(screen.getByRole('radio', { name: '부서' })).toBeTruthy()
    expect((screen.getByRole('radio', { name: '모든 사용자' }) as HTMLInputElement).checked).toBe(true)
    const select = screen.getByLabelText('권한 프리셋') as HTMLSelectElement
    const opts = Array.from(select.options).map((o) => o.value)
    expect(opts).toEqual(['read', 'upload', 'edit', 'share', 'admin'])
  })

  it('Submit body shape (everyone): {subject:{type:everyone,id:null}, preset, expiresAt 없음}', async () => {
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockResolvedValue(PERMISSION)
    const { onClose, onSuccess } = renderDialog()

    fireEvent.change(screen.getByLabelText('권한 프리셋'), { target: { value: 'edit' } })
    submit()

    await waitFor(() => expect(api.grantPermission).toHaveBeenCalledOnce())
    expect(api.grantPermission).toHaveBeenCalledWith('folder', 'fld_a', {
      subject: { type: 'everyone', id: null },
      preset: 'edit',
    })
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(PERMISSION))
    expect(onClose).toHaveBeenCalled()
    expect(toastSpy('success')).toHaveBeenCalledWith('권한을 부여했습니다')
  })

  it('expiresAt datetime-local → ISO 8601 변환 후 송신', async () => {
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockResolvedValue(PERMISSION)
    renderDialog()

    fireEvent.change(screen.getByLabelText(/만료/), { target: { value: '2026-12-31T23:59' } })
    submit()

    await waitFor(() => expect(api.grantPermission).toHaveBeenCalledOnce())
    const call = (api.grantPermission as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(call[2].expiresAt).toBe(new Date('2026-12-31T23:59').toISOString())
  })

  it('409 PERMISSION_CONFLICT → inline alert (다이얼로그 유지)', async () => {
    const apiError = Object.assign(new Error('grantPermission failed: 409'), {
      status: 409,
      code: 'PERMISSION_CONFLICT',
    })
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    const { onClose } = renderDialog()

    submit()

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/이미 부여된 grant/)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('403 PERMISSION_DENIED → toast.error + onClose', async () => {
    const apiError = Object.assign(new Error('grantPermission failed: 403'), {
      status: 403,
      code: 'PERMISSION_DENIED',
    })
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    const { onClose } = renderDialog()

    submit()

    await waitFor(() => expect(toastSpy('error')).toHaveBeenCalledWith('권한을 부여할 권한이 없습니다'))
    expect(onClose).toHaveBeenCalled()
  })

  it('404 NOT_FOUND → toast.error + onClose (resource label kind-aware)', async () => {
    const apiError = Object.assign(new Error('grantPermission failed: 404'), {
      status: 404,
      code: 'NOT_FOUND',
    })
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    const { onClose } = renderDialog()

    submit()

    await waitFor(() => expect(toastSpy('error')).toHaveBeenCalledWith('폴더을(를) 찾을 수 없습니다'))
    expect(onClose).toHaveBeenCalled()
  })

  it('400 VALIDATION_ERROR → inline alert (다이얼로그 유지) + onClose 미호출', async () => {
    const apiError = Object.assign(new Error('grantPermission failed: 400'), {
      status: 400,
      code: 'VALIDATION_ERROR',
    })
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    const { onClose } = renderDialog()

    submit()

    await waitFor(() => expect(screen.getByRole('alert').textContent).toMatch(/입력값이 올바르지 않습니다/))
    expect(onClose).not.toHaveBeenCalled()
  })

  it('알 수 없는 status code → 일반 inline alert fallback', async () => {
    const apiError = Object.assign(new Error('grantPermission failed: 500'), {
      status: 500,
      code: 'INTERNAL_SERVER_ERROR',
    })
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    const { onClose } = renderDialog()

    submit()

    await waitFor(() => expect(screen.getByRole('alert').textContent).toMatch(/권한 부여에 실패했습니다/))
    expect(onClose).not.toHaveBeenCalled()
  })

  // ─────────────────────────── Phase C — subject 분기 ───────────────────────────

  it('Phase C: user 라디오 클릭 → UserSearchCombobox 노출', () => {
    renderDialog()
    expect(screen.queryByPlaceholderText('사용자 이름 또는 이메일')).toBeNull()
    fireEvent.click(screen.getByRole('radio', { name: '특정 사용자' }))
    expect(screen.getByPlaceholderText('사용자 이름 또는 이메일')).toBeTruthy()
  })

  it('Phase C: department 라디오 클릭 → DepartmentSearchCombobox 노출', () => {
    renderDialog()
    expect(screen.queryByPlaceholderText('부서 이름')).toBeNull()
    fireEvent.click(screen.getByRole('radio', { name: '부서' }))
    expect(screen.getByPlaceholderText('부서 이름')).toBeTruthy()
  })

  it('Phase C: user subject 미선택 + submit → inline alert + api 미호출', async () => {
    renderDialog()
    fireEvent.click(screen.getByRole('radio', { name: '특정 사용자' }))
    submit()

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/사용자를 선택해 주세요/)
    })
    expect(api.grantPermission).not.toHaveBeenCalled()
  })

  it('Phase C: department subject 미선택 + submit → inline alert + api 미호출', async () => {
    renderDialog()
    fireEvent.click(screen.getByRole('radio', { name: '부서' }))
    submit()

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toMatch(/부서를 선택해 주세요/)
    })
    expect(api.grantPermission).not.toHaveBeenCalled()
  })

  it('Phase C: user 선택 + submit → body shape {type:user, id:UUID}', async () => {
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockResolvedValue(PERMISSION)
    setUserSearch([ALICE])
    renderDialog()
    fireEvent.click(screen.getByRole('radio', { name: '특정 사용자' }))

    // Combobox 입력 + listbox option 선택 (UserSearchCombobox 패턴)
    const input = screen.getByPlaceholderText('사용자 이름 또는 이메일') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    fireEvent.focus(input)
    // listbox 노출 후 option 클릭
    const opt = await screen.findByRole('option', { name: /Alice/ })
    fireEvent.click(opt)

    submit()

    await waitFor(() => expect(api.grantPermission).toHaveBeenCalledOnce())
    expect(api.grantPermission).toHaveBeenCalledWith('folder', 'fld_a', {
      subject: { type: 'user', id: 'usr-alice' },
      preset: 'read',
    })
  })

  it('Phase C: department 선택 + submit → body shape {type:department, id:UUID}', async () => {
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockResolvedValue(PERMISSION)
    setDeptSearch([ENG])
    renderDialog()
    fireEvent.click(screen.getByRole('radio', { name: '부서' }))

    const input = screen.getByPlaceholderText('부서 이름') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'En' } })
    fireEvent.focus(input)
    const opt = await screen.findByRole('option', { name: /Engineering/ })
    fireEvent.click(opt)

    submit()

    await waitFor(() => expect(api.grantPermission).toHaveBeenCalledOnce())
    expect(api.grantPermission).toHaveBeenCalledWith('folder', 'fld_a', {
      subject: { type: 'department', id: 'dep-eng' },
      preset: 'read',
    })
  })

  it('Phase C: user → department 라디오 전환 시 user 선택 초기화', () => {
    renderDialog()
    fireEvent.click(screen.getByRole('radio', { name: '특정 사용자' }))
    expect(screen.queryByPlaceholderText('사용자 이름 또는 이메일')).toBeTruthy()

    fireEvent.click(screen.getByRole('radio', { name: '부서' }))
    expect(screen.queryByPlaceholderText('사용자 이름 또는 이메일')).toBeNull()
    expect(screen.queryByPlaceholderText('부서 이름')).toBeTruthy()

    // 다시 user로 돌아오면 선택 빈 상태
    fireEvent.click(screen.getByRole('radio', { name: '특정 사용자' }))
    const input = screen.getByPlaceholderText('사용자 이름 또는 이메일') as HTMLInputElement
    expect(input.value).toBe('')
  })
})
