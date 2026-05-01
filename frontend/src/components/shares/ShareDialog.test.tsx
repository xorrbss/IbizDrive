import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ShareDialog } from './ShareDialog'
import { useShareUiStore } from '@/stores/shareUi'
import { useCreateShare } from '@/hooks/useCreateShare'
import { useRevokeShare } from '@/hooks/useRevokeShare'
import { useSharesByMe } from '@/hooks/useSharesByMe'
import { useUserSearch } from '@/hooks/useUserSearch'
import { useDepartmentSearch } from '@/hooks/useDepartmentSearch'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type { ShareDto } from '@/types/share'
import type { UserSummary } from '@/types/user'
import type { DepartmentSummary } from '@/types/department'

vi.mock('@/hooks/useCreateShare', () => ({ useCreateShare: vi.fn() }))
vi.mock('@/hooks/useRevokeShare', () => ({ useRevokeShare: vi.fn() }))
vi.mock('@/hooks/useSharesByMe', () => ({ useSharesByMe: vi.fn() }))
vi.mock('@/hooks/useUserSearch', () => ({ useUserSearch: vi.fn() }))
vi.mock('@/hooks/useDepartmentSearch', () => ({ useDepartmentSearch: vi.fn() }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

// F5.1 → A13: wire-aligned ShareDto. A13에서 permissions join으로 subjectType/subjectId/preset 복원.
const SHARE_FOR_FILE: ShareDto = {
  id: 'sh-1',
  fileId: 'f1',
  folderId: null,
  permissionId: 'p-1',
  sharedBy: 'me',
  message: null,
  expiresAt: null,
  createdAt: '2026-04-30T10:00:00Z',
  revokedAt: null,
  revokedBy: null,
  subjectType: 'everyone',
  subjectId: null,
  preset: 'edit',
  subjectName: null,
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
  ;(useUserSearch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: { items: [] },
    isLoading: false,
    isFetching: false,
  })
  ;(useDepartmentSearch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: { items: [] },
    isLoading: false,
    isFetching: false,
  })
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

const FILE_TARGET = { kind: 'file' as const, id: 'f1', name: 'doc.pdf' }

describe('ShareDialog (F5.1 wire-aligned)', () => {
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
    act(() => useShareUiStore.getState().open(FILE_TARGET))
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
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(useShareUiStore.getState().isOpen).toBe(false)
  })

  it('preset 변경 + 공유 버튼 → useCreateShare.mutate 호출', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: '편집' }))
    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    expect(mutate).toHaveBeenCalledTimes(1)
    const [vars] = mutate.mock.calls[0]
    expect(vars).toEqual({
      target: FILE_TARGET,
      req: { subjects: [{ type: 'everyone' }], preset: 'edit' },
    })
  })

  it('expiresAt 입력 → ISO 8601 변환 + 메시지 trim 포함', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
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
    act(() => useShareUiStore.getState().open(FILE_TARGET))
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
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    await waitFor(() =>
      expect(toastSpy('error')).toHaveBeenCalledWith('이미 같은 대상에게 공유되어 있습니다'),
    )
  })

  it('기존 공유 목록 — target.id로 (fileId ?? folderId) 필터, 해제 버튼 → useRevokeShare.mutate 호출', () => {
    const revokeMutate = vi.fn()
    setHooks({
      revokeMutate,
      shares: [SHARE_FOR_FILE, SHARE_OTHER_FILE],
    })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    // 다른 fileId share는 노출 안 됨
    expect(screen.getByText(/기존 공유 \(1\)/)).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '해제' }))
    expect(revokeMutate).toHaveBeenCalledTimes(1)
    expect(revokeMutate.mock.calls[0][0]).toBe('sh-1')
  })

  it('A13: 기존 공유 행에 subject + preset 라벨 노출 (everyone + edit → "모든 사용자 · 편집")', () => {
    setHooks({ shares: [SHARE_FOR_FILE] })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    // SHARE_FOR_FILE: subjectType='everyone', preset='edit' → 한국어 라벨 join.
    expect(screen.getByText('모든 사용자 · 편집')).toBeTruthy()
  })

  // ─── F6.4 — subject picker (user) 통합 ─────────────────────────────────────────
  it('F6.4: subjectType 라디오 — 기본 everyone, user 라디오 존재', () => {
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    const everyone = screen.getByRole('radio', { name: /모든 사용자/ }) as HTMLInputElement
    const user = screen.getByRole('radio', { name: /특정 사용자/ }) as HTMLInputElement
    expect(everyone.checked).toBe(true)
    expect(user.checked).toBe(false)
    // user 미선택 상태에서는 combobox 미노출
    expect(screen.queryByRole('combobox')).toBeNull()
  })

  it('F6.4: user 라디오 클릭 → UserSearchCombobox 노출', () => {
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /특정 사용자/ }))
    expect(screen.getByRole('combobox')).toBeTruthy()
  })

  it('F6.4: user 선택 + submit → subjects:[{type:user, id}] 페이로드', () => {
    const ALICE: UserSummary = { id: 'u1', displayName: 'Alice Kim', email: 'alice@example.com' }
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    setUserSearch([ALICE]) // setHooks 안에서 useUserSearch도 reset되므로 그 뒤에 override
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /특정 사용자/ }))
    const combo = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(combo, { target: { value: 'al' } })
    fireEvent.focus(combo)
    fireEvent.click(screen.getByText('Alice Kim'))

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    expect(mutate).toHaveBeenCalledTimes(1)
    const [vars] = mutate.mock.calls[0]
    expect(vars).toEqual({
      target: FILE_TARGET,
      req: { subjects: [{ type: 'user', id: 'u1' }], preset: 'read' },
    })
  })

  it('F6.4: user 라디오인데 미선택 + submit → mutate 차단 + toast.error', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /특정 사용자/ }))
    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    expect(mutate).not.toHaveBeenCalled()
    expect(toastSpy('error')).toHaveBeenCalledWith('공유할 사용자를 선택해 주세요')
  })

  it('F6.4: user → everyone 토글 → combobox 언마운트, submit subjects everyone', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /특정 사용자/ }))
    expect(screen.getByRole('combobox')).toBeTruthy()
    fireEvent.click(screen.getByRole('radio', { name: /모든 사용자/ }))
    expect(screen.queryByRole('combobox')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))
    expect(mutate).toHaveBeenCalledTimes(1)
    const [vars] = mutate.mock.calls[0]
    expect(vars.req.subjects).toEqual([{ type: 'everyone' }])
  })

  // ─── A16.7 — department subject picker 통합 ───────────────────────────────────
  it('A16.7: subjectType 라디오에 부서 추가 — 3-way (everyone | user | department)', () => {
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    expect(screen.getByRole('radio', { name: /모든 사용자/ })).toBeTruthy()
    expect(screen.getByRole('radio', { name: /특정 사용자/ })).toBeTruthy()
    expect(screen.getByRole('radio', { name: /부서/ })).toBeTruthy()
  })

  it('A16.7: department 라디오 클릭 → DepartmentSearchCombobox 노출', () => {
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /부서/ }))
    // 단일 combobox (dept picker)
    expect(screen.getByRole('combobox')).toBeTruthy()
  })

  it('A16.7: dept 선택 + submit → subjects:[{type:department, id}] 페이로드', () => {
    const ENG: DepartmentSummary = { id: 'd1', name: 'Engineering' }
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    setDeptSearch([ENG])
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /부서/ }))
    const combo = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(combo, { target: { value: 'en' } })
    fireEvent.focus(combo)
    fireEvent.click(screen.getByText('Engineering'))

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    expect(mutate).toHaveBeenCalledTimes(1)
    const [vars] = mutate.mock.calls[0]
    expect(vars).toEqual({
      target: FILE_TARGET,
      req: { subjects: [{ type: 'department', id: 'd1' }], preset: 'read' },
    })
  })

  it('A16.7: department 라디오인데 미선택 + submit → mutate 차단 + toast.error', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /부서/ }))
    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))

    expect(mutate).not.toHaveBeenCalled()
    expect(toastSpy('error')).toHaveBeenCalledWith('공유할 부서를 선택해 주세요')
  })

  it('A16.7: user → department 토글 → user combobox 사라지고 dept combobox 마운트', () => {
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    fireEvent.click(screen.getByRole('radio', { name: /특정 사용자/ }))
    const userCombo = screen.getByRole('combobox') as HTMLInputElement
    expect(userCombo.placeholder).toMatch(/사용자/)

    fireEvent.click(screen.getByRole('radio', { name: /부서/ }))
    const deptCombo = screen.getByRole('combobox') as HTMLInputElement
    expect(deptCombo.placeholder).toMatch(/부서/)
  })

  it('A16.7: existing share에 subjectName 노출 — user displayName 우선', () => {
    const SHARE_USER: ShareDto = {
      ...SHARE_FOR_FILE,
      id: 'sh-user',
      subjectType: 'user',
      subjectId: 'u1',
      subjectName: 'Alice Kim',
      preset: 'read',
    }
    setHooks({ shares: [SHARE_USER] })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    expect(screen.getByText('Alice Kim · 읽기')).toBeTruthy()
  })

  it('A16.7: existing share에 subjectName 노출 — department name 우선', () => {
    const SHARE_DEPT: ShareDto = {
      ...SHARE_FOR_FILE,
      id: 'sh-dept',
      subjectType: 'department',
      subjectId: 'd1',
      subjectName: 'Engineering',
      preset: 'edit',
    }
    setHooks({ shares: [SHARE_DEPT] })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    expect(screen.getByText('Engineering · 편집')).toBeTruthy()
  })

  it('A16.7: subjectName 미존재 시 fallback (`사용자 ${head}`)', () => {
    const SHARE_USER_NO_NAME: ShareDto = {
      ...SHARE_FOR_FILE,
      id: 'sh-noname',
      subjectType: 'user',
      subjectId: 'abcdef12-aaaa-bbbb-cccc-deadbeefdead',
      subjectName: null,
      preset: 'read',
    }
    setHooks({ shares: [SHARE_USER_NO_NAME] })
    act(() => useShareUiStore.getState().open(FILE_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    expect(screen.getByText(/사용자 abcdef12 · 읽기/)).toBeTruthy()
  })

  it('F5.2: folder kind 진입 → mutate Vars target.kind=folder + 부제 "폴더" 라벨', () => {
    const mutate = vi.fn()
    setHooks({ createMutate: mutate })
    const FOLDER_TARGET = { kind: 'folder' as const, id: 'fld-1', name: '문서함' }
    act(() => useShareUiStore.getState().open(FOLDER_TARGET))
    const qc = new QueryClient()
    render(<ShareDialog />, { wrapper: wrap(qc) })

    expect(screen.getByText('문서함')).toBeTruthy()
    // 부제에 '폴더' 라벨 노출
    expect(screen.getByText(/폴더 공유 설정/)).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: /^공유$/ }))
    expect(mutate).toHaveBeenCalledTimes(1)
    const [vars] = mutate.mock.calls[0]
    expect(vars).toEqual({
      target: FOLDER_TARGET,
      req: { subjects: [{ type: 'everyone' }], preset: 'read' },
    })
  })
})
