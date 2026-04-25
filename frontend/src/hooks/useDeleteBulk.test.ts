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

// Mock api.deleteBulk + restoreFiles (M9: Undo 토스트가 restore 호출)
// + sonner. vi.mock는 hoist되므로 모든 mock vars는 vi.hoisted()로.
const { deleteBulkMock, restoreFilesMock, toastFn, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  deleteBulkMock: vi.fn(),
  restoreFilesMock: vi.fn(),
  toastFn: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}))

vi.mock('@/lib/api', () => ({
  api: {
    deleteBulk: (...args: unknown[]) => deleteBulkMock(...args),
    restoreFiles: (...args: unknown[]) => restoreFilesMock(...args),
  },
}))

vi.mock('sonner', () => ({
  toast: Object.assign(toastFn, {
    success: toastSuccessMock,
    error: toastErrorMock,
  }),
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
    restoreFilesMock.mockReset()
    toastFn.mockReset()
    toastSuccessMock.mockReset()
    toastErrorMock.mockReset()
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

    // M9: Undo 토스트가 호출되어야 함 (action: 되돌리기 포함)
    expect(toastFn).toHaveBeenCalledWith(
      expect.stringContaining('휴지통으로 이동'),
      expect.objectContaining({
        action: expect.objectContaining({ label: '되돌리기' }),
        duration: 5000,
      }),
    )
  })

  it('Undo 토스트 action 클릭 → api.restoreFiles 호출', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    deleteBulkMock.mockResolvedValue({ deletedIds: ['a', 'b'] })
    restoreFilesMock.mockResolvedValue({ restoredIds: ['a', 'b'] })

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], folderIdAtStart: 'root' })
    })

    await waitFor(() => expect(toastFn).toHaveBeenCalled())

    // toast() call의 action.onClick을 직접 실행
    const lastCall = toastFn.mock.calls.at(-1)
    const action = lastCall?.[1]?.action
    await act(async () => {
      await action.onClick()
    })

    expect(restoreFilesMock).toHaveBeenCalledWith(['a', 'b'])
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
