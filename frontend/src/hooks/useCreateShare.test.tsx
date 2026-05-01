import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useCreateShare } from './useCreateShare'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { ShareDto } from '@/types/share'

vi.mock('@/lib/api', () => ({
  api: { createFileShares: vi.fn(), createFolderShares: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

// F5.1 → A13: wire-aligned ShareDto. A13에서 permissions join으로 subjectType/subjectId/preset 복원.
const SHARE: ShareDto = {
  id: 'sh-1',
  fileId: 'file-1',
  folderId: null,
  permissionId: 'perm-1',
  sharedBy: 'me',
  message: null,
  expiresAt: null,
  createdAt: '2026-04-30T12:00:00Z',
  revokedAt: null,
  revokedBy: null,
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
}

describe('useCreateShare', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('file target → api.createFileShares 호출 + qk.shares() invalidate', async () => {
    ;(api.createFileShares as ReturnType<typeof vi.fn>).mockResolvedValue([SHARE])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCreateShare(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        target: { kind: 'file', id: 'file-1', name: 'doc.pdf' },
        req: { subjects: [{ type: 'everyone' }], preset: 'read' },
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.createFileShares).toHaveBeenCalledWith('file-1', {
      subjects: [{ type: 'everyone' }],
      preset: 'read',
    })
    expect(api.createFolderShares).not.toHaveBeenCalled()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.shares() })
    expect(result.current.data).toEqual([SHARE])
  })

  it('folder target → api.createFolderShares 호출 + qk.shares() invalidate (F5.2)', async () => {
    const FOLDER_SHARE: ShareDto = {
      ...SHARE,
      id: 'sh-folder-1',
      fileId: null,
      folderId: 'folder-1',
    }
    ;(api.createFolderShares as ReturnType<typeof vi.fn>).mockResolvedValue([FOLDER_SHARE])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCreateShare(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        target: { kind: 'folder', id: 'folder-1', name: '문서함' },
        req: { subjects: [{ type: 'everyone' }], preset: 'read' },
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.createFolderShares).toHaveBeenCalledWith('folder-1', {
      subjects: [{ type: 'everyone' }],
      preset: 'read',
    })
    expect(api.createFileShares).not.toHaveBeenCalled()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.shares() })
    expect(result.current.data).toEqual([FOLDER_SHARE])
  })

  it('409 PERMISSION_CONFLICT → isError + invalidate skip', async () => {
    const err = Object.assign(new Error('createFileShares failed: 409'), {
      status: 409,
      code: 'PERMISSION_CONFLICT',
    })
    ;(api.createFileShares as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCreateShare(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        target: { kind: 'file', id: 'file-1', name: 'doc.pdf' },
        req: { subjects: [{ type: 'everyone' }], preset: 'read' },
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { code?: string })?.code).toBe('PERMISSION_CONFLICT')
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
