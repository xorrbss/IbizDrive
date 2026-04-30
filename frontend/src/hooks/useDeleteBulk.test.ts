import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useDeleteBulk } from './useDeleteBulk'
import { useSelectionStore } from '@/stores/selection'

// Mock useCurrentFolder to control currentFolderId
let mockFolderId = 'root'
vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: () => ({
    folderId: mockFolderId,
    folder: null,
    breadcrumb: [],
    isLoading: false,
    error: null,
  }),
}))

// M9.1 — Mock api.deleteBulk 제거 → softDeleteFile/softDeleteFolder per-item
const softDeleteFileMock = vi.fn()
const softDeleteFolderMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: {
    softDeleteFile: (id: string) => softDeleteFileMock(id),
    softDeleteFolder: (id: string) => softDeleteFolderMock(id),
  },
}))

const makeWrapper = (qc: QueryClient) => {
  const Wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
  Wrapper.displayName = 'QCWrapper'
  return Wrapper
}

describe('useDeleteBulk', () => {
  beforeEach(() => {
    softDeleteFileMock.mockReset()
    softDeleteFolderMock.mockReset()
    useSelectionStore.setState({
      ids: new Set(),
      lastClickedId: null,
      pendingIds: new Set(),
    })
    mockFolderId = 'root'
  })

  it('성공: file/folder 분기 호출 + markPending → invalidate → unmarkPending → clear', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    softDeleteFileMock.mockResolvedValue(undefined)
    softDeleteFolderMock.mockResolvedValue(undefined)

    useSelectionStore.getState().selectAll(['a', 'b', 'c'])

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({
        items: [
          { id: 'a', type: 'file' },
          { id: 'b', type: 'folder' },
        ],
        folderIdAtStart: 'root',
      })
    })

    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0)
      expect(useSelectionStore.getState().ids.size).toBe(0)
    })

    expect(softDeleteFileMock).toHaveBeenCalledWith('a')
    expect(softDeleteFolderMock).toHaveBeenCalledWith('b')
    expect(invalidateSpy).toHaveBeenCalled()
  })

  it('실패 + 같은 폴더: selection 복원', async () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    softDeleteFileMock.mockRejectedValue(new Error('network'))
    softDeleteFolderMock.mockResolvedValue(undefined)

    useSelectionStore.getState().selectAll(['a', 'b'])
    mockFolderId = 'root'

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({
        items: [
          { id: 'a', type: 'file' },
          { id: 'b', type: 'folder' },
        ],
        folderIdAtStart: 'root',
      })
    })

    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0)
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'b'])
    })
  })

  it('실패 + 다른 폴더: 복원 스킵', async () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    softDeleteFileMock.mockRejectedValue(new Error('network'))
    softDeleteFolderMock.mockRejectedValue(new Error('network'))

    useSelectionStore.getState().selectAll(['a', 'b'])
    mockFolderId = 'folder_other' // 시작 시 current != start

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({
        items: [
          { id: 'a', type: 'file' },
          { id: 'b', type: 'file' },
        ],
        folderIdAtStart: 'root',
      })
    })

    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0)
      expect(useSelectionStore.getState().ids.size).toBe(0)
    })
  })
})
