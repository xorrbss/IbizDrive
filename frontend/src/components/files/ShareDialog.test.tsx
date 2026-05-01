import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ShareDialog } from './ShareDialog'
import { useShareUiStore } from '@/stores/shareUi'
import { useCreateShare } from '@/hooks/useCreateShare'
import { useRevokeShare } from '@/hooks/useRevokeShare'
import { useSharesByMe } from '@/hooks/useSharesByMe'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type { ShareDto } from '@/types/share'

vi.mock('@/hooks/useCreateShare', () => ({ useCreateShare: vi.fn() }))
vi.mock('@/hooks/useRevokeShare', () => ({ useRevokeShare: vi.fn() }))
vi.mock('@/hooks/useSharesByMe', () => ({ useSharesByMe: vi.fn() }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const SHARE_FOR_FILE: ShareDto = {
  id: 'sh-1',
  fileId: 'f1',
  permissionId: 'p-1',
  sharedBy: 'me',
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
  expiresAt: null,
  message: null,
  createdAt: '2026-04-30T10:00:00Z',
}
const SHARE_OTHER_FILE: ShareDto = {
  ...SHARE_FOR_FILE,
  id: 'sh-2',
  fileId: 'other-file',
}

function setHooks(opts: {
  createMutate?: ReturnType<typeof vi.fn>
  revokeMutate?: ReturnType<typeof vi.fn>
  isCreatePending?: boolean
  isRevokePending?: boolean
  shares?: ShareDto[]
} = {}) {
  ;(useCreateShare as ReturnType<typeof vi.fn>).mockReturnValue({
    mutate: opts.createMutate ?? vi.fn(),
    isPending: opts.isCreatePending ?? false,
  })
  ;(useRevokeShare as ReturnType<typeof vi.fn>).mockReturnValue({
    mutate: opts.revokeMutate ?? vi.fn(),
    isPending: opts.isRevokePending ?? false,
  })
  ;(useSharesByMe as ReturnType<typeof vi.fn>).mockReturnValue({
    data: opts.shares
      ? { pages: [{ items: opts.shares, nextCursor: null }] }
      : { pages: [{ items: [], nextCursor: null }] },
  })
}

describe('ShareDialog (F4)', () => {
  beforeEach(() => {
    resetSonnerToastMock()
    vi.clearAllMocks()
    act(() => useShareUiStore.getState().close())
    setHooks()
  })

  it('isOpen=false 일 때 렌더링 안 됨', () => {
    const qc = new QueryClient()
    const { container } = render(<ShareDialog />, { wrapper: wrap(qc) })
    expect(container.children.length).toBe(0)
  })

  it('open 시 dialog + 파일명 + everyone 라벨 + 4 preset radio', () => {
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('doc.pdf')).toBeTruthy()
    expect(screen.getByText(/모든 사용자/)).toBeTruthy()
    // 4 preset
    expect(screen.getByRole('radio', { name: '읽기' })).toBeTruthy()
    expect(screen.getByRole('radio', { name: '업로드' })).toBeTruthy()
    expect(screen.getByRole('radio', { name: '편집' })).toBeTruthy()
    expect(screen.getByRole('radio', { name: '관리' })).toBeTruthy()
    // 기본값 = read
    expect((screen.getByRole('radio', { name: '읽기' }) as HTMLInputElement).checked).toBe(true)
  })

  it('Esc → close', () => {
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(useShareUiStore.getState().isOpen).toBe(false)
  })

  it('preset 변경 + 공유 버튼 → useCreateShare.mutate 호출', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: '편집' }))
    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    expect(mutate).toHaveBeenCalledTimes(1)
    const [vars] = mutate.mock.calls[0]
    expect(vars).toEqual({
      fileId: 'f1',
      req: { subjects: [{ type: 'everyone' }], preset: 'edit' },
    })
  })

  it('expiresAt 입력 → ISO 8601 변환 + 메시지 trim 포함', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    const expiresInput = screen.getByLabelText(/만료/) as HTMLInputElement
    fireEvent.change(expiresInput, { target: { value: '2026-12-31T23:59' } })
    const msgInput = screen.getByLabelText(/메시지/) as HTMLTextAreaElement
    fireEvent.change(msgInput, { target: { value: '  안녕하세요  ' } })

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    expect(mutate).toHaveBeenCalledTimes(1)
    const [vars] = mutate.mock.calls[0]
    expect(vars.req.expiresAt).toMatch(/^2026-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.000Z$/)
    expect(vars.req.message).toBe('안녕하세요')
  })

  it('성공 시 toast.success + close', async () => {
    const mutate = vi.fn((_vars, opts: { onSuccess?: () => void }) => {
      opts.onSuccess?.()
    })
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    await waitFor(() => expect(toastSpy('success')).toHaveBeenCalledWith('공유했습니다'))
    expect(useShareUiStore.getState().isOpen).toBe(false)
  })

  it('409 PERMISSION_CONFLICT → 한국어 toast.error', async () => {
    const mutate = vi.fn(
      (_vars, opts: { onError?: (e: Error) => void }) => {
        const err = Object.assign(new Error('conflict'), {
          status: 409,
          code: 'PERMISSION_CONFLICT',
        })
        opts.onError?.(err)
      },
    )
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    await waitFor(() =>
      expect(toastSpy('error')).toHaveBeenCalledWith('이미 같은 대상에게 공유되어 있습니다'),
    )
  })

  it('기존 공유 목록 — fileId로 필터, 해제 버튼 → useRevokeShare.mutate 호출', () => {
    const revokeMutate = vi.fn()
    setHooks({
      revokeMutate,
      shares: [SHARE_FOR_FILE, SHARE_OTHER_FILE],
    })
    act(() => useShareUiStore.getState().open('f1', 'doc.pdf'))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    // 다른 fileId share는 노출 안 됨
    expect(screen.getByText(/기존 공유 \(1\)/)).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '해제' }))
    expect(revokeMutate).toHaveBeenCalledTimes(1)
    expect(revokeMutate.mock.calls[0][0]).toBe('sh-1')
  })
})
