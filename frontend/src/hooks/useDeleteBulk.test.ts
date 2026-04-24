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

// Mock api.deleteBulk
const deleteBulkMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: { deleteBulk: (...args: unknown[]) => deleteBulkMock(...args) },
}))

const makeWrapper = (qc: QueryClient) => {
  const Wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
  Wrapper.displayName = 'QCWrapper'
  return Wrapper
}

describe('useDeleteBulk', () => {
  beforeEach(() => {
    deleteBulkMock.mockReset()
    useSelectionStore.setState({
      ids: new Set(),
      lastClickedId: null,
      pendingIds: new Set(),
    })
    mockFolderId = 'root'
  })

  it('성공: markPending → invalidate → unmarkPending → clear 순서', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    deleteBulkMock.mockResolvedValue({ deletedIds: ['a', 'b'] })

    useSelectionStore.getState().selectAll(['a', 'b', 'c'])

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], folderIdAtStart: 'root' })
    })

    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0)
      expect(useSelectionStore.getState().ids.size).toBe(0)
    })

    expect(invalidateSpy).toHaveBeenCalled()
  })

  it('실패 + 같은 폴더: selection 복원', async () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    deleteBulkMock.mockRejectedValue(new Error('network'))

    useSelectionStore.getState().selectAll(['a', 'b'])
    mockFolderId = 'root'

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], folderIdAtStart: 'root' })
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
    deleteBulkMock.mockRejectedValue(new Error('network'))

    useSelectionStore.getState().selectAll(['a', 'b'])
    mockFolderId = 'folder_other' // 시작 시 current != start

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], folderIdAtStart: 'root' })
    })

    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0)
      expect(useSelectionStore.getState().ids.size).toBe(0)
    })
  })
})
