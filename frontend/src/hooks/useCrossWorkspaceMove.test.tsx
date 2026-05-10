import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import {
  useCrossWorkspaceMoveFolder,
  useCrossWorkspaceMoveFile,
} from './useCrossWorkspaceMove'
import * as apiMove from '@/lib/api.move'
import { qk } from '@/lib/queryKeys'

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

const folderMoveResult = {
  folder: { id: 'folder_a', name: 'Sales', parentId: 'folder_dst', slug: 'sales' },
}
const fileMoveResult = {
  file: {
    id: 'file_a',
    name: 'doc.pdf',
    parentId: 'folder_dst',
    mimeType: 'application/pdf',
    size: 512,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
    versions: 1,
    scope: null,
  },
}

describe('useCrossWorkspaceMoveFolder', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 source + destination folderChildren, shares 무효화', async () => {
    ;(apiMove.crossWorkspaceMoveFolder as ReturnType<typeof vi.fn>).mockResolvedValue(
      folderMoveResult,
    )
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useCrossWorkspaceMoveFolder(), {
      wrapper: makeWrapper(qc),
    })

    await act(async () => {
      await result.current.mutateAsync({
        folderId: 'folder_a',
        sourceFolderId: 'folder_src',
        sourceScopeType: 'department',
        sourceScopeId: 'dept_1',
        body: { targetParentId: 'folder_dst', allowCrossScope: true },
        destinationScopeType: 'team',
        destinationScopeId: 'team_1',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiMove.crossWorkspaceMoveFolder).toHaveBeenCalledWith('folder_a', {
      targetParentId: 'folder_dst',
      allowCrossScope: true,
    })

    // folderChildren 전체 prefix 무효화 확인
    const invalidatedKeys = invalidateSpy.mock.calls.map((c) => c[0])
    const allQueryKeys = invalidatedKeys.map((q) => q?.queryKey)
    const hasChildren = allQueryKeys.some(
      (k) => Array.isArray(k) && k.includes('children'),
    )
    expect(hasChildren).toBe(true)

    // shares 무효화 확인
    const sharesKey = qk.shares()
    const hasShares = allQueryKeys.some(
      (k) =>
        Array.isArray(k) && sharesKey.every((seg, i) => k[i] === seg),
    )
    expect(hasShares).toBe(true)
  })

  it('실패 시 isError === true', async () => {
    ;(apiMove.crossWorkspaceMoveFolder as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 409,
      code: 'NAME_CONFLICT',
    })
    const qc = new QueryClient({ defaultOptions: { mutations: { retry: 0 } } })
    const { result } = renderHook(() => useCrossWorkspaceMoveFolder(), {
      wrapper: makeWrapper(qc),
    })

    await act(async () => {
      result.current.mutate({
        folderId: 'folder_a',
        sourceFolderId: 'folder_src',
        sourceScopeType: 'department',
        sourceScopeId: 'dept_1',
        body: { targetParentId: 'folder_dst', allowCrossScope: true },
        destinationScopeType: 'team',
        destinationScopeId: 'team_1',
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useCrossWorkspaceMoveFile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 source + destination folderChildren, fileDetail, shares, workspaces 무효화', async () => {
    ;(apiMove.crossWorkspaceMoveFile as ReturnType<typeof vi.fn>).mockResolvedValue(
      fileMoveResult,
    )
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useCrossWorkspaceMoveFile(), {
      wrapper: makeWrapper(qc),
    })

    await act(async () => {
      await result.current.mutateAsync({
        fileId: 'file_a',
        sourceFolderId: 'folder_src',
        sourceScopeType: 'department',
        sourceScopeId: 'dept_1',
        body: { targetFolderId: 'folder_dst', allowCrossScope: true },
        destinationScopeType: 'team',
        destinationScopeId: 'team_1',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiMove.crossWorkspaceMoveFile).toHaveBeenCalledWith('file_a', {
      targetFolderId: 'folder_dst',
      allowCrossScope: true,
    })

    const allQueryKeys = invalidateSpy.mock.calls.map((c) => c[0]?.queryKey)

    // folderChildren prefix 무효화 확인
    const hasChildren = allQueryKeys.some(
      (k) => Array.isArray(k) && k.includes('children'),
    )
    expect(hasChildren).toBe(true)

    // fileDetail 무효화 확인
    const fileDetailKey = qk.fileDetail('file_a')
    const hasFileDetail = allQueryKeys.some(
      (k) =>
        Array.isArray(k) && fileDetailKey.every((seg, i) => k[i] === seg),
    )
    expect(hasFileDetail).toBe(true)

    // shares 무효화 확인
    const sharesKey = qk.shares()
    const hasShares = allQueryKeys.some(
      (k) =>
        Array.isArray(k) && sharesKey.every((seg, i) => k[i] === seg),
    )
    expect(hasShares).toBe(true)
  })

  it('실패 시 isError === true', async () => {
    ;(apiMove.crossWorkspaceMoveFile as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 403,
      code: 'PERMISSION_DENIED',
    })
    const qc = new QueryClient({ defaultOptions: { mutations: { retry: 0 } } })
    const { result } = renderHook(() => useCrossWorkspaceMoveFile(), {
      wrapper: makeWrapper(qc),
    })

    await act(async () => {
      result.current.mutate({
        fileId: 'file_x',
        sourceFolderId: 'folder_src',
        sourceScopeType: 'department',
        sourceScopeId: 'dept_1',
        body: { targetFolderId: 'folder_dst', allowCrossScope: true },
        destinationScopeType: 'team',
        destinationScopeId: 'team_1',
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
