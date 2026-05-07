import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useCreateFolder } from './useCreateFolder'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({
  api: {
    createFolder: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useCreateFolder', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.createFolder 호출 + 3개 키 무효화 (filesListPrefix / folderTree / folder)', async () => {
    ;(api.createFolder as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'new-id',
      name: '새 폴더',
      parentId: 'p1',
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCreateFolder(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ parentId: 'p1', name: '새 폴더' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.createFolder).toHaveBeenCalledWith('p1', '새 폴더')
    const calls = invalidateSpy.mock.calls.map((c) => c[0])
    expect(calls).toEqual(
      expect.arrayContaining([
        { queryKey: qk.filesListPrefix('p1') },
        { queryKey: qk.folderTree() },
        { queryKey: qk.folder('p1') },
      ]),
    )
  })

  it('가상 root parentId 그대로 전달 (api 래퍼가 null로 변환)', async () => {
    ;(api.createFolder as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'new-id',
      name: 'X',
      parentId: null,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useCreateFolder(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ parentId: 'root', name: 'X' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.createFolder).toHaveBeenCalledWith('root', 'X')
  })

  it('409 RENAME_CONFLICT → isError + envelope code 보존, invalidate 미호출', async () => {
    const err = Object.assign(new Error('createFolder failed: 409'), {
      status: 409,
      code: 'RENAME_CONFLICT',
    })
    ;(api.createFolder as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCreateFolder(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ parentId: 'p1', name: '중복' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { code?: string })?.code).toBe(
      'RENAME_CONFLICT',
    )
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
