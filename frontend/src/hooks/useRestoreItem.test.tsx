import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useRestoreItem } from './useRestoreItem'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({
  api: {
    restoreFile: vi.fn(),
    restoreFolder: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useRestoreItem', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('file 타입 → api.restoreFile 호출 + 무효화 (folderIds 지정 시 filesListPrefix 포함)', async () => {
    ;(api.restoreFile as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRestoreItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'file', id: 'f1', sourceFolderId: 'p1' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.restoreFile).toHaveBeenCalledWith('f1')
    expect(api.restoreFolder).not.toHaveBeenCalled()
    // afterRestore — trash + search + folderTree + filesListPrefix(p1)
    const calls = invalidateSpy.mock.calls.map((c) => c[0])
    expect(calls).toEqual(
      expect.arrayContaining([
        { queryKey: qk.trash() },
        { queryKey: qk.search() },
        { queryKey: qk.folderTree() },
        { queryKey: qk.filesListPrefix('p1') },
      ]),
    )
  })

  it('folder 타입 → api.restoreFolder 호출', async () => {
    ;(api.restoreFolder as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useRestoreItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'folder', id: 'd1' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.restoreFolder).toHaveBeenCalledWith('d1')
    expect(api.restoreFile).not.toHaveBeenCalled()
  })

  it('sourceFolderId 미지정 → afterRestore가 qk.files() 보수 무효화', async () => {
    ;(api.restoreFile as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRestoreItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'file', id: 'f1' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const calls = invalidateSpy.mock.calls.map((c) => c[0])
    expect(calls).toEqual(
      expect.arrayContaining([{ queryKey: qk.files() }]),
    )
  })

  it('409 RESTORE_CONFLICT → isError + invalidate skip', async () => {
    const err = Object.assign(new Error('restoreFile failed: 409'), {
      status: 409,
      code: 'RESTORE_CONFLICT',
    })
    ;(api.restoreFile as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRestoreItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'file', id: 'f1', sourceFolderId: 'p1' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { code?: string })?.code).toBe(
      'RESTORE_CONFLICT',
    )
    // 실패 시 invalidateQueries는 호출되지 않음 (onError에 invalidate 없음)
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
