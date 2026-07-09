import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { BulkActionBar } from './BulkActionBar'
import { useSelectionStore } from '@/stores/selection'
import { useRenameUiStore } from '@/stores/renameUi'
import { useShareUiStore } from '@/stores/shareUi'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { api } from '@/lib/api'
import { toastSpy } from '@/test/mocks/sonner'
import type { FileItem } from '@/types/file'
import type { PermissionFlags } from '@/hooks/usePermission'

const FULL_PERMISSION_FLAGS: PermissionFlags = {
  READ: true,
  UPLOAD: true,
  EDIT: true,
  MOVE: true,
  DOWNLOAD: true,
  DELETE: true,
  SHARE: true,
  PERMISSION_ADMIN: true,
  PURGE: false,
  APPROVE_ADMIN_ACTION: false,
}

const NO_PERMISSION_FLAGS: PermissionFlags = {
  READ: false,
  UPLOAD: false,
  EDIT: false,
  MOVE: false,
  DOWNLOAD: false,
  DELETE: false,
  SHARE: false,
  PERMISSION_ADMIN: false,
  PURGE: false,
  APPROVE_ADMIN_ACTION: false,
}

const { permissionFlags } = vi.hoisted(() => ({
  permissionFlags: {
    current: {
      READ: true,
      UPLOAD: true,
      EDIT: true,
      MOVE: true,
      DOWNLOAD: true,
      DELETE: true,
      SHARE: true,
      PERMISSION_ADMIN: true,
      PURGE: false,
      APPROVE_ADMIN_ACTION: false,
    } as PermissionFlags,
  },
}))

vi.mock('@/hooks/useFilesInFolder', () => ({
  useFilesInFolder: vi.fn(),
}))

// M8: usePermission은 useQuery 기반 — 테스트는 권한 검증과 무관하므로 admin preset 8 권한으로 고정.
vi.mock('@/hooks/usePermission', () => ({
  usePermission: () => permissionFlags.current,
}))

vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: vi.fn(),
}))

vi.mock('@/hooks/useSortParams', () => ({
  useSortParams: vi.fn(),
}))

vi.mock('@/hooks/useDeleteBulk', () => ({
  useDeleteBulk: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

vi.mock('@/lib/api', () => ({
  api: {
    restoreFile: vi.fn(),
    restoreFolder: vi.fn(),
    downloadFile: vi.fn(),
    getEffectivePermissions: vi.fn(),
  },
}))

const ITEMS: FileItem[] = [
  {
    id: 'f1',
    name: 'alpha.txt',
    type: 'file',
    mimeType: 'text/plain',
    size: 100,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
  {
    id: 'f2',
    name: 'beta.txt',
    type: 'file',
    mimeType: 'text/plain',
    size: 200,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
  {
    id: 'fo1',
    name: '폴더A',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
]

function makeWrapper() {
  const qc = new QueryClient()
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

beforeEach(() => {
  permissionFlags.current = FULL_PERMISSION_FLAGS
  vi.mocked(api.getEffectivePermissions).mockResolvedValue([
    'READ',
    'UPLOAD',
    'EDIT',
    'MOVE',
    'DOWNLOAD',
    'DELETE',
    'SHARE',
    'PERMISSION_ADMIN',
  ])
})

describe('BulkActionBar — 이름 변경 버튼', () => {
  beforeEach(() => {
    // selection / rename store 초기화
    act(() => {
      useSelectionStore.getState().clear()
      useRenameUiStore.getState().close()
    })
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: ITEMS,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    vi.mocked(useCurrentFolder).mockReturnValue({
      folderId: 'root',
      folder: { id: 'root', name: 'root', slugPath: [] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useCurrentFolder>)
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc', setSort: vi.fn() })
  })

  it('단일 선택 시 활성: 클릭하면 RenameDialog가 열린다 (id, name 전달)', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    expect(btn.getAttribute('title')).toBeNull()
    act(() => {
      fireEvent.click(btn)
    })
    const state = useRenameUiStore.getState()
    expect(state.isOpen).toBe(true)
    expect(state.targetId).toBe('f1')
    expect(state.targetName).toBe('alpha.txt')
  })

  it('다중 선택 시 비활성 + tooltip', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
      useSelectionStore.getState().toggle('f2')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    expect(btn.getAttribute('title')).toBe('단일 선택 시 사용 가능')
    // 클릭해도 다이얼로그가 열리지 않음 (disabled가 click 방지)
    act(() => {
      fireEvent.click(btn)
    })
    expect(useRenameUiStore.getState().isOpen).toBe(false)
  })

  it('단일 선택이지만 cache miss(items=undefined)면 비활성', () => {
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
  })

  it('단일 선택이 폴더여도 활성 (정책: 폴더 이름 변경 허용)', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('fo1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    act(() => {
      fireEvent.click(btn)
    })
    expect(useRenameUiStore.getState().targetId).toBe('fo1')
    expect(useRenameUiStore.getState().targetName).toBe('폴더A')
  })
})

// ─── 공유 버튼 — file/folder 단일 선택 시 활성, 다중은 비활성 ─────────────────
//
// 폴더 공유 endpoint(A12) + ShareDialog folder 분기(F5.2) + useCreateShare folder
// 변형이 모두 closed → BulkActionBar 차원에서 폴더 공유 진입을 막을 이유가 없다.
// 다중(2+) 선택은 단일 호출 wire(POST /:id/share)와 매칭되지 않아 비활성 유지.

describe('BulkActionBar — 공유 버튼', () => {
  beforeEach(() => {
    act(() => {
      useSelectionStore.getState().clear()
      useShareUiStore.getState().close()
    })
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: ITEMS,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    vi.mocked(useCurrentFolder).mockReturnValue({
      folderId: 'root',
      folder: { id: 'root', name: 'root', slugPath: [] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useCurrentFolder>)
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc', setSort: vi.fn() })
  })

  it('단일 파일 선택 시 활성: 클릭하면 ShareDialog가 file kind로 열린다', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '공유' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    expect(btn.getAttribute('title')).toBeNull()
    act(() => {
      fireEvent.click(btn)
    })
    const state = useShareUiStore.getState()
    expect(state.isOpen).toBe(true)
    expect(state.target).toEqual({ kind: 'file', id: 'f1', name: 'alpha.txt' })
  })

  it('단일 폴더 선택 시 활성: 클릭하면 ShareDialog가 folder kind로 열린다', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('fo1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '공유' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    expect(btn.getAttribute('title')).toBeNull()
    act(() => {
      fireEvent.click(btn)
    })
    const state = useShareUiStore.getState()
    expect(state.isOpen).toBe(true)
    expect(state.target).toEqual({ kind: 'folder', id: 'fo1', name: '폴더A' })
  })

  it('다중 선택 시 비활성 + tooltip', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
      useSelectionStore.getState().toggle('f2')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '공유' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    expect(btn.getAttribute('title')).toBe('단일 항목 선택 시 사용 가능')
    act(() => {
      fireEvent.click(btn)
    })
    expect(useShareUiStore.getState().isOpen).toBe(false)
  })

  it('단일 선택이지만 cache miss(items=undefined)면 비활성', () => {
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '공유' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
  })
})

// ─── M-Download: 다운로드 버튼 — file-only 가드 ─────────────────────────────
//
// backend `GET /api/files/{id}/download` (docs/02 §7.6.1)는 파일 단건만 지원
// (폴더 zip은 별도 트랙). BulkActionBar는 단일 파일 선택 시에만 활성, 폴더 / 다중
// / 캐시 미스는 비활성. 클릭 시 api.downloadFile(id) 호출 — 진행률은 브라우저
// 다운로드 매니저 책임이므로 fire-and-forget.

describe('BulkActionBar — 다운로드 버튼 (M-Download)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => {
      useSelectionStore.getState().clear()
    })
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: ITEMS,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    vi.mocked(useCurrentFolder).mockReturnValue({
      folderId: 'root',
      folder: { id: 'root', name: 'root', slugPath: [] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useCurrentFolder>)
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc', setSort: vi.fn() })
  })

  it('단일 파일 선택 시 활성: 클릭하면 api.downloadFile(id) 호출', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '다운로드' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    expect(btn.getAttribute('title')).toBeNull()
    act(() => {
      fireEvent.click(btn)
    })
    expect(api.downloadFile).toHaveBeenCalledTimes(1)
    expect(api.downloadFile).toHaveBeenCalledWith('f1')
  })

  it('단일 폴더 선택 시 비활성 + tooltip("파일만 다운로드 가능")', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('fo1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '다운로드' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    expect(btn.getAttribute('title')).toBe('파일만 다운로드 가능')
    act(() => {
      fireEvent.click(btn)
    })
    expect(api.downloadFile).not.toHaveBeenCalled()
  })

  it('다중 선택 시 비활성 + tooltip("단일 파일 선택 시 사용 가능")', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
      useSelectionStore.getState().toggle('f2')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '다운로드' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    expect(btn.getAttribute('title')).toBe('단일 파일 선택 시 사용 가능')
    act(() => {
      fireEvent.click(btn)
    })
    expect(api.downloadFile).not.toHaveBeenCalled()
  })

  it('단일 선택이지만 cache miss(items=undefined)면 비활성', () => {
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '다운로드' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
  })
})

describe('BulkActionBar — 삭제 버튼 권한 표시', () => {
  beforeEach(() => {
    act(() => {
      useSelectionStore.getState().clear()
    })
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: ITEMS,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    vi.mocked(useCurrentFolder).mockReturnValue({
      folderId: 'root',
      folder: { id: 'root', name: 'root', slugPath: [] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useCurrentFolder>)
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc', setSort: vi.fn() })
  })

  it('전역 role 권한이 없어도 선택 항목에 DELETE가 있으면 활성화', async () => {
    const mutate = vi.fn()
    permissionFlags.current = NO_PERMISSION_FLAGS
    vi.mocked(api.getEffectivePermissions).mockResolvedValue(['READ', 'DELETE'])
    vi.mocked(useDeleteBulk).mockReturnValue({
      mutate,
      isPending: false,
    } as unknown as ReturnType<typeof useDeleteBulk>)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })

    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '휴지통으로' })

    await waitFor(() => expect((btn as HTMLButtonElement).disabled).toBe(false))
    expect(btn.getAttribute('title')).toBeNull()
    act(() => {
      fireEvent.click(btn)
    })
    expect(mutate).toHaveBeenCalledWith({
      items: [{ id: 'f1', type: 'file' }],
      folderIdAtStart: 'root',
    })
  })

  it('DELETE가 없으면 휴지통 버튼을 비활성 + 사유 tooltip으로 노출', async () => {
    permissionFlags.current = NO_PERMISSION_FLAGS
    vi.mocked(api.getEffectivePermissions).mockResolvedValue(['READ', 'UPLOAD'])
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })

    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '휴지통으로' })

    await waitFor(() =>
      expect(btn.getAttribute('title')).toBe('선택한 항목을 삭제할 권한이 없습니다'),
    )
    expect((btn as HTMLButtonElement).disabled).toBe(true)
  })
})

// ─── M9.4: Undo toast wiring ───────────────────────────────────────────────
//
// 삭제 mutation onSuccess 콜백을 BulkActionBar가 useDeleteBulk(options)에 등록 →
// 토스트에 5초 짜리 '되돌리기' action을 붙임. 액션 클릭 시 api.restoreFile/Folder
// 호출 + invalidations.afterRestore로 캐시 무효화.

describe('BulkActionBar — Undo toast (M9.4)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => {
      useSelectionStore.getState().clear()
    })
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: ITEMS,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    vi.mocked(useCurrentFolder).mockReturnValue({
      folderId: 'root',
      folder: { id: 'root', name: 'root', slugPath: [] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useCurrentFolder>)
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc', setSort: vi.fn() })
  })

  /**
   * onSuccess를 캡쳐하고 직접 호출하여, BulkActionBar가 등록한 콜백이
   * toast.success를 어떻게 호출하는지 검증한다.
   */
  function setupCaptureOnSuccess() {
    const mutate = vi.fn()
    let captured:
      | ((vars: { items: { id: string; type: 'file' | 'folder' }[]; folderIdAtStart: string }) => void)
      | undefined
    vi.mocked(useDeleteBulk).mockImplementation((opts) => {
      captured = opts?.onSuccess
      return { mutate, isPending: false } as unknown as ReturnType<typeof useDeleteBulk>
    })
    return {
      mutate,
      fire: (vars: {
        items: { id: string; type: 'file' | 'folder' }[]
        folderIdAtStart: string
      }) => captured?.(vars),
    }
  }

  it('삭제 성공 토스트는 duration:5000 + action:{ label: "되돌리기" }를 포함', () => {
    const { fire } = setupCaptureOnSuccess()
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })

    fire({ items: [{ id: 'f1', type: 'file' }], folderIdAtStart: 'root' })

    const successCalls = toastSpy('success').mock.calls
    expect(successCalls.length).toBe(1)
    const [, opts] = successCalls[0]
    expect(opts?.duration).toBe(5000)
    expect((opts as { action?: { label: string } } | undefined)?.action?.label).toBe(
      '되돌리기',
    )
  })

  it('action.onClick → api.restoreFile/Folder를 type 분기로 호출', async () => {
    const { fire } = setupCaptureOnSuccess()
    vi.mocked(api.restoreFile).mockResolvedValue(undefined)
    vi.mocked(api.restoreFolder).mockResolvedValue(undefined)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })

    fire({
      items: [
        { id: 'f1', type: 'file' },
        { id: 'fo1', type: 'folder' },
      ],
      folderIdAtStart: 'root',
    })

    const [, opts] = toastSpy('success').mock.calls[0]
    const action = (opts as { action?: { onClick: () => void } } | undefined)?.action
    expect(action).toBeTruthy()

    await act(async () => {
      action?.onClick()
    })

    await waitFor(() => {
      expect(api.restoreFile).toHaveBeenCalledWith('f1')
      expect(api.restoreFolder).toHaveBeenCalledWith('fo1')
    })
  })

  it('Undo 성공 시 후속 toast.success("복원했습니다") 발생', async () => {
    const { fire } = setupCaptureOnSuccess()
    vi.mocked(api.restoreFile).mockResolvedValue(undefined)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })

    fire({ items: [{ id: 'f1', type: 'file' }], folderIdAtStart: 'root' })
    const [, opts] = toastSpy('success').mock.calls[0]
    const action = (opts as { action?: { onClick: () => void } } | undefined)?.action

    await act(async () => {
      action?.onClick()
    })

    await waitFor(() => {
      const calls = toastSpy('success').mock.calls
      const restoreToast = calls.find(
        ([msg]) => typeof msg === 'string' && msg.includes('복원'),
      )
      expect(restoreToast).toBeTruthy()
    })
  })

  it('Undo 실패(RESTORE_CONFLICT) → toast.error("같은 이름")', async () => {
    const { fire } = setupCaptureOnSuccess()
    const err = Object.assign(new Error('restoreFile failed: 409'), {
      status: 409,
      code: 'RESTORE_CONFLICT',
    })
    vi.mocked(api.restoreFile).mockRejectedValue(err)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })

    fire({ items: [{ id: 'f1', type: 'file' }], folderIdAtStart: 'root' })
    const [, opts] = toastSpy('success').mock.calls[0]
    const action = (opts as { action?: { onClick: () => void } } | undefined)?.action

    await act(async () => {
      action?.onClick()
    })

    await waitFor(() => {
      const errorCalls = toastSpy('error').mock.calls
      const conflictToast = errorCalls.find(
        ([msg]) => typeof msg === 'string' && msg.includes('같은 이름'),
      )
      expect(conflictToast).toBeTruthy()
    })
  })
})
