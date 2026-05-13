import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useUpdateAdminTrashPolicy } from './useUpdateAdminTrashPolicy'
import * as api from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { ApprovalRequiredError } from '@/lib/errors'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    updateAdminTrashPolicy: vi.fn(),
  }
})

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useUpdateAdminTrashPolicy', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
  })

  it('성공 시 qk.adminTrashPolicy() invalidate 호출', async () => {
    ;(api.updateAdminTrashPolicy as ReturnType<typeof vi.fn>).mockResolvedValue({ retentionDays: 14 })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useUpdateAdminTrashPolicy(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate(14)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.updateAdminTrashPolicy).toHaveBeenCalledWith(14)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.adminTrashPolicy() })
    expect(result.current.data).toEqual({ retentionDays: 14 })
  })

  it('실패 시 invalidate 미호출 + error pass-through', async () => {
    const apiError = Object.assign(new Error('updateAdminTrashPolicy failed: 400'), {
      status: 400,
      code: 'VALIDATION_ERROR',
    })
    ;(api.updateAdminTrashPolicy as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useUpdateAdminTrashPolicy(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate(6)
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(invalidateSpy).not.toHaveBeenCalled()
    expect(result.current.error).toBe(apiError)
  })

  it('202 APPROVAL_REQUIRED → ApprovalRequiredError + toast.info + invalidate 미호출', async () => {
    const apprErr = new ApprovalRequiredError(
      'aaaa-bbbb',
      '2026-05-15T00:00:00Z',
    )
    ;(api.updateAdminTrashPolicy as ReturnType<typeof vi.fn>).mockRejectedValue(apprErr)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useUpdateAdminTrashPolicy(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate(14)
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(ApprovalRequiredError)
    expect(invalidateSpy).not.toHaveBeenCalled()
    await waitFor(() => {
      const calls = toastSpy('info').mock.calls
      expect(calls.length).toBeGreaterThan(0)
      expect(calls[0][0]).toMatch(/휴지통 보존 정책 변경/)
    })
  })
})
