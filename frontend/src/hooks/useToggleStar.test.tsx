import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useToggleStar } from './useToggleStar'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import type { FileItem } from '@/types/file'
import type { FolderDetail } from '@/types/folder'

vi.mock('@/lib/api', () => ({
  api: { toggleStar: vi.fn() },
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const FILE: FileItem = {
  id: 'f1',
  name: 'a.pdf',
  type: 'file',
  mimeType: 'application/pdf',
  size: 1000,
  updatedAt: '2026-05-14T00:00:00Z',
  updatedBy: 'me',
  parentId: 'folder_parent',
}

describe('useToggleStar', () => {
  beforeEach(() => vi.clearAllMocks())

  it('star → 다음 상태 true로 backend 호출 + list cache 즉시 갱신', async () => {
    ;(api.toggleStar as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient()
    qc.setQueryData<FileItem[]>(
      [...qk.filesListPrefix('folder_parent'), 'name', 'asc'],
      [FILE],
    )

    const { result } = renderHook(() => useToggleStar(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        resourceType: 'file',
        id: 'f1',
        parentId: 'folder_parent',
        currentStarred: false,
      })
    })

    // 낙관적 갱신: onMutate가 async이므로 waitFor로 flush.
    await waitFor(() => {
      const optimistic = qc.getQueryData<FileItem[]>(
        [...qk.filesListPrefix('folder_parent'), 'name', 'asc'],
      )
      expect(optimistic?.[0].starred).toBe(true)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.toggleStar).toHaveBeenCalledWith('file', 'f1', true)
  })

  it('unstar (currentStarred=true) → 다음 상태 false로 backend 호출 + list cache starred=undefined', async () => {
    ;(api.toggleStar as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient()
    qc.setQueryData<FileItem[]>(
      [...qk.filesListPrefix('folder_parent'), 'name', 'asc'],
      [{ ...FILE, starred: true }],
    )

    const { result } = renderHook(() => useToggleStar(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        resourceType: 'file',
        id: 'f1',
        parentId: 'folder_parent',
        currentStarred: true,
      })
    })

    await waitFor(() => {
      const optimistic = qc.getQueryData<FileItem[]>(
        [...qk.filesListPrefix('folder_parent'), 'name', 'asc'],
      )
      expect(optimistic?.[0].starred).toBeUndefined()
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.toggleStar).toHaveBeenCalledWith('file', 'f1', false)
  })

  it('folder 토글 → folder detail cache의 starred도 동시 갱신', async () => {
    ;(api.toggleStar as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient()
    const folderDetail: FolderDetail = {
      id: 'folder_x',
      name: '영업팀',
      slugPath: ['영업팀'],
      breadcrumb: [],
      parentId: 'root',
      starred: false,
    }
    qc.setQueryData(qk.folder('folder_x'), folderDetail)

    const { result } = renderHook(() => useToggleStar(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        resourceType: 'folder',
        id: 'folder_x',
        parentId: 'root',
        currentStarred: false,
      })
    })

    await waitFor(() => {
      const optimistic = qc.getQueryData<FolderDetail>(qk.folder('folder_x'))
      expect(optimistic?.starred).toBe(true)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('에러 시 rollback — list cache 원복', async () => {
    ;(api.toggleStar as ReturnType<typeof vi.fn>).mockRejectedValue({
      status: 403,
      code: 'PERMISSION_DENIED',
    })
    const qc = new QueryClient()
    qc.setQueryData<FileItem[]>(
      [...qk.filesListPrefix('folder_parent'), 'name', 'asc'],
      [FILE],
    )

    const { result } = renderHook(() => useToggleStar(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        resourceType: 'file',
        id: 'f1',
        parentId: 'folder_parent',
        currentStarred: false,
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    // rollback: starred 원래대로 undefined
    const after = qc.getQueryData<FileItem[]>(
      [...qk.filesListPrefix('folder_parent'), 'name', 'asc'],
    )
    expect(after?.[0].starred).toBeUndefined()
  })

  it('onSettled — list + (folder인 경우) folder(id) invalidate', async () => {
    ;(api.toggleStar as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useToggleStar(), {
      wrapper: makeWrapper(qc),
    })

    act(() => {
      result.current.mutate({
        resourceType: 'folder',
        id: 'folder_x',
        parentId: 'root',
        currentStarred: false,
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const keysInvalidated = invalidateSpy.mock.calls.map((c) =>
      JSON.stringify(c[0]?.queryKey ?? []),
    )
    expect(keysInvalidated.some((k) => k.includes('"list"') && k.includes('"root"'))).toBe(true)
    expect(keysInvalidated.some((k) => k.includes('"detail"') && k.includes('"folder_x"'))).toBe(true)
  })
})
