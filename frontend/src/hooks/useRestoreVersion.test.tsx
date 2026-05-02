import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useRestoreVersion } from './useRestoreVersion'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({
  api: {
    restoreVersion: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useRestoreVersion (M-RP.2.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.restoreVersion(fileId, versionId) 호출 + fileDetail/fileVersions/files() 무효화', async () => {
    ;(api.restoreVersion as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRestoreVersion(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ fileId: 'file_a', versionId: 'v_old' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.restoreVersion).toHaveBeenCalledWith('file_a', 'v_old')

    const calls = invalidateSpy.mock.calls.map((c) => c[0])
    // parentFolderId omit → qk.files() 보수 무효화 (list view의 size 컬럼 신선화).
    expect(calls).toEqual(
      expect.arrayContaining([
        { queryKey: qk.fileDetail('file_a') },
        { queryKey: qk.fileVersions('file_a') },
        { queryKey: qk.files() },
      ]),
    )
  })

  it('parentFolderId 제공 시 filesListPrefix(folderId)로 정확한 prefix 무효화', async () => {
    ;(api.restoreVersion as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRestoreVersion(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        fileId: 'file_a',
        versionId: 'v_old',
        parentFolderId: 'folder_x',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const calls = invalidateSpy.mock.calls.map((c) => c[0])
    expect(calls).toEqual(
      expect.arrayContaining([
        { queryKey: qk.filesListPrefix('folder_x') },
      ]),
    )
    // 보수 무효화(qk.files())는 parentFolderId 알 때 호출되지 않아야 한다.
    const keys = invalidateSpy.mock.calls.map((c) =>
      JSON.stringify((c[0] as { queryKey: readonly unknown[] }).queryKey),
    )
    expect(keys).not.toContain(JSON.stringify(qk.files()))
  })

  it('options.onSuccess 콜백 발화', async () => {
    ;(api.restoreVersion as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const onSuccess = vi.fn()
    const { result } = renderHook(() => useRestoreVersion({ onSuccess }), {
      wrapper: wrap(qc),
    })

    act(() => {
      result.current.mutate({ fileId: 'file_a', versionId: 'v_old' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(onSuccess).toHaveBeenCalledWith({ fileId: 'file_a', versionId: 'v_old' })
  })

  it('403 EDIT 미보유 → isError + invalidate skip + onError 콜백', async () => {
    const err = Object.assign(new Error('restoreVersion failed: 403'), {
      status: 403,
    })
    ;(api.restoreVersion as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const onError = vi.fn()
    const { result } = renderHook(() => useRestoreVersion({ onError }), {
      wrapper: wrap(qc),
    })

    act(() => {
      result.current.mutate({ fileId: 'file_a', versionId: 'v_old' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { status?: number })?.status).toBe(403)
    expect(onError).toHaveBeenCalled()
    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('404 cross-file/missing version → isError', async () => {
    const err = Object.assign(new Error('restoreVersion failed: 404'), {
      status: 404,
    })
    ;(api.restoreVersion as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useRestoreVersion(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ fileId: 'file_a', versionId: 'stray_v' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { status?: number })?.status).toBe(404)
  })
})
