import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useRevokeShare } from './useRevokeShare'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({
  api: { revokeShare: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useRevokeShare', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.revokeShare 호출 + qk.shares() invalidate', async () => {
    ;(api.revokeShare as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRevokeShare(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate('sh-1')
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.revokeShare).toHaveBeenCalledWith('sh-1')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.shares() })
  })

  it('403 PERMISSION_DENIED → isError + invalidate skip', async () => {
    const err = Object.assign(new Error('revokeShare failed: 403'), {
      status: 403,
      code: 'PERMISSION_DENIED',
    })
    ;(api.revokeShare as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRevokeShare(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate('sh-1')
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { code?: string })?.code).toBe('PERMISSION_DENIED')
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
