import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { usePurgeTrashItem } from './usePurgeTrashItem'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({
  api: { purgeTrashItem: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('usePurgeTrashItem', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.purgeTrashItem 호출 + qk.trash() 무효화', async () => {
    ;(api.purgeTrashItem as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => usePurgeTrashItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'file', id: 'f1' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.purgeTrashItem).toHaveBeenCalledWith('file', 'f1')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.trash() })
  })

  it('folder 타입도 type 파라미터 그대로 전달', async () => {
    ;(api.purgeTrashItem as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => usePurgeTrashItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'folder', id: 'd1' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.purgeTrashItem).toHaveBeenCalledWith('folder', 'd1')
  })

  it('403 → isError + invalidate skip (비-ADMIN)', async () => {
    const err = Object.assign(new Error('purgeTrashItem failed: 403'), { status: 403 })
    ;(api.purgeTrashItem as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => usePurgeTrashItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'file', id: 'f1' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { status?: number })?.status).toBe(403)
    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('404 → isError (이미 purge된 항목)', async () => {
    const err = Object.assign(new Error('purgeTrashItem failed: 404'), { status: 404 })
    ;(api.purgeTrashItem as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => usePurgeTrashItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ type: 'file', id: 'f1' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { status?: number })?.status).toBe(404)
  })
})
