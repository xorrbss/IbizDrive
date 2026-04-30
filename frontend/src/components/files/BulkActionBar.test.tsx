import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { BulkActionBar } from './BulkActionBar'
import { useSelectionStore } from '@/stores/selection'
import { useRenameUiStore } from '@/stores/renameUi'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type { FileItem } from '@/types/file'

vi.mock('@/hooks/useFilesInFolder', () => ({
  useFilesInFolder: vi.fn(),
}))

// M8: usePermission은 useQuery 기반 — 테스트는 권한 검증과 무관하므로 admin preset 8 권한으로 고정.
vi.mock('@/hooks/usePermission', () => ({
  usePermission: () => ({
    READ: true,
    UPLOAD: true,
    EDIT: true,
    MOVE: true,
    DOWNLOAD: true,
    DELETE: true,
    SHARE: true,
    PERMISSION_ADMIN: true,
    PURGE: false,
  }),
}))

vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: vi.fn(),
}))

vi.mock('@/hooks/useSortParams', () => ({
  useSortParams: vi.fn(),
}))

const { deleteOptionsCapture, restoreMutateSpy } = vi.hoisted(() => ({
  deleteOptionsCapture: { current: null as null | { onSuccess?: (v: { ids: string[]; folderIdAtStart: string }) => void; onError?: (e: unknown, v: { ids: string[]; folderIdAtStart: string }) => void } },
  restoreMutateSpy: vi.fn(),
}))

vi.mock('@/hooks/useDeleteBulk', () => ({
  useDeleteBulk: (opts?: typeof deleteOptionsCapture.current) => {
    deleteOptionsCapture.current = opts ?? null
    return { mutate: vi.fn(), isPending: false }
  },
}))

vi.mock('@/hooks/useRestoreBulk', () => ({
  useRestoreBulk: () => ({ mutate: restoreMutateSpy, isPending: false }),
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
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc' })
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

describe('BulkActionBar — 휴지통 Undo 토스트 (M9)', () => {
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
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc' })
    deleteOptionsCapture.current = null
    restoreMutateSpy.mockReset()
    resetSonnerToastMock()
  })

  it('delete 성공 시 toast.success가 5초 + 되돌리기 액션과 함께 호출', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
      useSelectionStore.getState().toggle('f2')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    expect(deleteOptionsCapture.current?.onSuccess).toBeTypeOf('function')

    // 시뮬레이션: useDeleteBulk가 mutate 성공 후 onSuccess 호출
    act(() => {
      deleteOptionsCapture.current?.onSuccess?.({ ids: ['f1', 'f2'], folderIdAtStart: 'root' })
    })

    const success = toastSpy('success')
    expect(success).toHaveBeenCalledTimes(1)
    expect(success).toHaveBeenCalledWith(
      '2개 항목을 휴지통으로 이동했습니다',
      expect.objectContaining({
        duration: 5000,
        action: expect.objectContaining({ label: '되돌리기' }),
      }),
    )
  })

  it('Undo 액션 클릭 시 useRestoreBulk.mutate가 originalParentIds 포함하여 호출', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })

    act(() => {
      deleteOptionsCapture.current?.onSuccess?.({ ids: ['f1'], folderIdAtStart: 'folder_sales' })
    })

    const success = toastSpy('success')
    const opts = success.mock.calls[0][1] as { action?: { onClick: () => void } }
    act(() => {
      opts.action?.onClick?.()
    })

    expect(restoreMutateSpy).toHaveBeenCalledWith({
      ids: ['f1'],
      originalParentIds: ['folder_sales'],
    })
  })

  it('delete 실패 시 toast.error', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    act(() => {
      deleteOptionsCapture.current?.onError?.(new Error('boom'), { ids: ['f1'], folderIdAtStart: 'root' })
    })
    expect(toastSpy('error')).toHaveBeenCalledWith('삭제에 실패했습니다. 다시 시도해 주세요.')
  })
})
