import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useRenameFile } from './useRenameFile'
import { useSelectionStore } from '@/stores/selection'
import { useRenameUiStore } from '@/stores/renameUi'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { renameFile: vi.fn() },
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useRenameFile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSelectionStore.setState({
      ids: new Set(),
      pendingIds: new Set(),
      lastClickedId: null,
    })
    useRenameUiStore.setState({
      isOpen: true,
      targetId: 'file_x',
      targetName: 'old.txt',
      error: null,
    })
  })

  it('onMutate에서 targetId pending 마크', () => {
    ;(api.renameFile as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'file_x',
      name: 'new.txt',
      parentId: 'root',
      type: 'file',
    })
    const qc = new QueryClient()
    const { result } = renderHook(() => useRenameFile(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        id: 'file_x',
        newName: 'new.txt',
        parentId: 'root',
        isFolder: false,
      })
    })

    expect(useSelectionStore.getState().pendingIds.has('file_x')).toBe(true)
  })

  it('성공 시 invalidate(filesInFolder, fileDetail) + close + unmarkPending', async () => {
    ;(api.renameFile as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'file_x',
      name: 'new.txt',
      parentId: 'root',
      type: 'file',
    })
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRenameFile(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        id: 'file_x',
        newName: 'new.txt',
        parentId: 'root',
        isFolder: false,
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalled()
    expect(useSelectionStore.getState().pendingIds.size).toBe(0)
    expect(useRenameUiStore.getState().isOpen).toBe(false)
  })

  it('폴더 rename 성공 시 folderTree도 invalidate', async () => {
    ;(api.renameFile as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'folder_x',
      name: '새이름',
      parentId: 'root',
      type: 'folder',
    })
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRenameFile(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        id: 'folder_x',
        newName: '새이름',
        parentId: 'root',
        isFolder: true,
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const calls = invalidateSpy.mock.calls.map((c) => c[0])
    expect(calls.some((arg) => JSON.stringify(arg).includes('"tree"'))).toBe(true)
  })

  it('RENAME_CONFLICT 실패 → setError + 다이얼로그 유지 + unmarkPending', async () => {
    ;(api.renameFile as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 409,
      code: 'RENAME_CONFLICT',
    })
    const qc = new QueryClient()
    const { result } = renderHook(() => useRenameFile(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        id: 'file_x',
        newName: 'dup.txt',
        parentId: 'root',
        isFolder: false,
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    const ui = useRenameUiStore.getState()
    expect(ui.isOpen).toBe(true)
    expect(ui.error).toContain('이름')
    expect(useSelectionStore.getState().pendingIds.size).toBe(0)
  })

  it('VALIDATION_ERROR 실패 → 다른 에러 메시지', async () => {
    ;(api.renameFile as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
    const qc = new QueryClient()
    const { result } = renderHook(() => useRenameFile(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        id: 'file_x',
        newName: '   ',
        parentId: 'root',
        isFolder: false,
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    const ui = useRenameUiStore.getState()
    expect(ui.error).toContain('비어')
  })
})
