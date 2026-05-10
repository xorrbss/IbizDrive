import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { GrantPermissionDialog } from './GrantPermissionDialog'
import { api } from '@/lib/api'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type { PermissionListItem } from '@/types/permission'

vi.mock('@/lib/api', () => ({
  api: { grantPermission: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
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

function submit() {
  fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)
}

describe('GrantPermissionDialog (Phase B)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
  })

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

  it('preset 5값 모두 노출 + 대상 = everyone 헤더', () => {
    renderDialog()
    expect(screen.getByText(/전체 사용자\(everyone\)/)).toBeTruthy()
    const select = screen.getByLabelText('권한 프리셋') as HTMLSelectElement
    const opts = Array.from(select.options).map((o) => o.value)
    expect(opts).toEqual(['read', 'upload', 'edit', 'share', 'admin'])
  })

  it('Submit body shape: {subject:{type:everyone,id:null}, preset, expiresAt 없음}', async () => {
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

})
