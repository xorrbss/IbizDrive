import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useMoveFolderPreview, useMoveFilePreview } from './useMovePreview'
import * as apiMove from '@/lib/api.move'

vi.mock('@/lib/api.move', () => ({
  previewFolderMove: vi.fn(),
  previewFileMove: vi.fn(),
  crossWorkspaceMoveFolder: vi.fn(),
  crossWorkspaceMoveFile: vi.fn(),
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const mockPreviewResponse = {
  itemCount: 2,
  removedPermissions: [],
  revokedShares: [],
  targetMembershipDefaults: [],
  nameConflict: null,
}

describe('useMoveFolderPreview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('previewFolderMove 호출 후 MovePreviewResponse 반환', async () => {
    ;(apiMove.previewFolderMove as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockPreviewResponse,
    )
    const qc = new QueryClient()
    const { result } = renderHook(() => useMoveFolderPreview(), {
      wrapper: makeWrapper(qc),
    })

    let data: typeof mockPreviewResponse | undefined
    await act(async () => {
      data = await result.current.mutateAsync({
        folderId: 'folder_a',
        body: { destinationFolderId: 'folder_b' },
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(data).toEqual(mockPreviewResponse)
    expect(apiMove.previewFolderMove).toHaveBeenCalledWith('folder_a', {
      destinationFolderId: 'folder_b',
    })
  })

  it('실패 시 isError === true', async () => {
    ;(apiMove.previewFolderMove as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 404,
      code: 'TARGET_NOT_FOUND',
    })
    const qc = new QueryClient({ defaultOptions: { mutations: { retry: 0 } } })
    const { result } = renderHook(() => useMoveFolderPreview(), {
      wrapper: makeWrapper(qc),
    })

    await act(async () => {
      result.current.mutate({
        folderId: 'folder_x',
        body: { destinationFolderId: 'no_such' },
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useMoveFilePreview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('previewFileMove 호출 후 MovePreviewResponse 반환', async () => {
    ;(apiMove.previewFileMove as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockPreviewResponse,
    )
    const qc = new QueryClient()
    const { result } = renderHook(() => useMoveFilePreview(), {
      wrapper: makeWrapper(qc),
    })

    let data: typeof mockPreviewResponse | undefined
    await act(async () => {
      data = await result.current.mutateAsync({
        fileId: 'file_a',
        body: { destinationFolderId: 'folder_b' },
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(data).toEqual(mockPreviewResponse)
    expect(apiMove.previewFileMove).toHaveBeenCalledWith('file_a', {
      destinationFolderId: 'folder_b',
    })
  })

  it('실패 시 isError === true', async () => {
    ;(apiMove.previewFileMove as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 409,
      code: 'ERR_CROSS_SCOPE_MOVE',
    })
    const qc = new QueryClient({ defaultOptions: { mutations: { retry: 0 } } })
    const { result } = renderHook(() => useMoveFilePreview(), {
      wrapper: makeWrapper(qc),
    })

    await act(async () => {
      result.current.mutate({
        fileId: 'file_x',
        body: { destinationFolderId: 'other_scope' },
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
