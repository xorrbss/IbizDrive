import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useMoveBulk } from './useMoveBulk'
import { useSelectionStore } from '@/stores/selection'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { moveFiles: vi.fn() },
}))

function makeWrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('useMoveBulk', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSelectionStore.setState({
      ids: new Set(['a', 'b']),
      pendingIds: new Set(),
      lastClickedId: null,
    })
  })

  it('onMutate에서 markPending 적용', () => {
    ;(api.moveFiles as ReturnType<typeof vi.fn>).mockResolvedValue({
      movedIds: ['a', 'b'],
    })
    const qc = new QueryClient()
    const { result } = renderHook(() => useMoveBulk(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        ids: ['a', 'b'],
        sourceFolderId: 'src',
        targetFolderId: 'dst',
      })
    })

    expect(useSelectionStore.getState().pendingIds.has('a')).toBe(true)
    expect(useSelectionStore.getState().pendingIds.has('b')).toBe(true)
  })

  it('성공 시 무효화 + unmarkPending + clear', async () => {
    ;(api.moveFiles as ReturnType<typeof vi.fn>).mockResolvedValue({
      movedIds: ['a', 'b'],
    })
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useMoveBulk(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        ids: ['a', 'b'],
        sourceFolderId: 'src',
        targetFolderId: 'dst',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalled()
    expect(useSelectionStore.getState().pendingIds.size).toBe(0)
    expect(useSelectionStore.getState().ids.size).toBe(0)
  })

  it('실패 시 unmarkPending만, ids 복구 X', async () => {
    ;(api.moveFiles as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 400,
      code: 'MOVE_INTO_SELF',
    })
    const qc = new QueryClient()
    const { result } = renderHook(() => useMoveBulk(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        ids: ['a', 'b'],
        sourceFolderId: 'src',
        targetFolderId: 'dst',
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(useSelectionStore.getState().pendingIds.size).toBe(0)
  })
})
