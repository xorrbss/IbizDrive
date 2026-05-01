import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useCreateShare } from './useCreateShare'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { ShareDto } from '@/types/share'

vi.mock('@/lib/api', () => ({
  api: { createShares: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const SHARE: ShareDto = {
  id: 'sh-1',
  fileId: 'file-1',
  permissionId: 'perm-1',
  sharedBy: 'me',
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
  expiresAt: null,
  message: null,
  createdAt: '2026-04-30T12:00:00Z',
}

describe('useCreateShare', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.createShares 호출 + qk.shares() invalidate', async () => {
    ;(api.createShares as ReturnType<typeof vi.fn>).mockResolvedValue([SHARE])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCreateShare(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        fileId: 'file-1',
        req: { subjects: [{ type: 'everyone' }], preset: 'read' },
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.createShares).toHaveBeenCalledWith('file-1', {
      subjects: [{ type: 'everyone' }],
      preset: 'read',
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.shares() })
    expect(result.current.data).toEqual([SHARE])
  })

  it('409 PERMISSION_CONFLICT → isError + invalidate skip', async () => {
    const err = Object.assign(new Error('createShares failed: 409'), {
      status: 409,
      code: 'PERMISSION_CONFLICT',
    })
    ;(api.createShares as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useCreateShare(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        fileId: 'file-1',
        req: { subjects: [{ type: 'everyone' }], preset: 'read' },
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { code?: string })?.code).toBe('PERMISSION_CONFLICT')
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
