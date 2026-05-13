import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import {
  useAdminTrashList,
  useAdminRestoreTrashItem,
  useAdminPurgeTrashItem,
  useAdminBulkTrash,
} from './useAdminTrash'
import { api, adminListTrash, adminBulkTrash } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { ApprovalRequiredError } from '@/lib/errors'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type {
  AdminTrashBulkResponse,
  AdminTrashFilters,
  AdminTrashPage,
} from '@/types/trash'

vi.mock('@/lib/api', () => ({
  adminListTrash: vi.fn(),
  adminBulkTrash: vi.fn(),
  api: {
    restoreFile: vi.fn(),
    restoreFolder: vi.fn(),
    purgeTrashItem: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const samplePage: AdminTrashPage = {
  items: [],
  nextCursor: null,
}

describe('useAdminTrashList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → adminListTrash(filters, cursor) 호출 + queryKey 일치', async () => {
    ;(adminListTrash as ReturnType<typeof vi.fn>).mockResolvedValue(samplePage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const filters: AdminTrashFilters = {
      q: 'rep',
      type: 'file',
      ownerId: null,
      deletedFrom: null,
      deletedTo: null,
    }
    const { result } = renderHook(
      () => useAdminTrashList(filters, null),
      { wrapper: wrap(qc) },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(adminListTrash).toHaveBeenCalledWith(filters, null)
    expect(result.current.data).toEqual(samplePage)
  })
})

describe('useAdminRestoreTrashItem', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('file 타입 → api.restoreFile 호출 + qk.adminTrash() 무효화', async () => {
    ;(api.restoreFile as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useAdminRestoreTrashItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: 'f1', type: 'file' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.restoreFile).toHaveBeenCalledWith('f1')
    expect(api.restoreFolder).not.toHaveBeenCalled()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.adminTrash() })
  })

  it('folder 타입 → api.restoreFolder 호출 + qk.adminTrash() 무효화', async () => {
    ;(api.restoreFolder as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useAdminRestoreTrashItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: 'd1', type: 'folder' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.restoreFolder).toHaveBeenCalledWith('d1')
    expect(api.restoreFile).not.toHaveBeenCalled()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.adminTrash() })
  })
})

describe('useAdminPurgeTrashItem', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 → api.purgeTrashItem(type, id) 호출 + qk.adminTrash() 무효화', async () => {
    ;(api.purgeTrashItem as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useAdminPurgeTrashItem(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ id: 'f1', type: 'file' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.purgeTrashItem).toHaveBeenCalledWith('file', 'f1')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.adminTrash() })
  })
})

describe('useAdminBulkTrash', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetSonnerToastMock()
  })

  it('성공 → adminBulkTrash(action, items) 호출 + qk.adminTrash() 무효화 + 응답 echo', async () => {
    const response: AdminTrashBulkResponse = {
      succeeded: [{ type: 'file', id: 'f1' }],
      failed: [{ type: 'folder', id: 'd1', error: 'NAME_CONFLICT' }],
    }
    ;(adminBulkTrash as ReturnType<typeof vi.fn>).mockResolvedValue(response)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useAdminBulkTrash(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        action: 'restore',
        items: [
          { type: 'file', id: 'f1' },
          { type: 'folder', id: 'd1' },
        ],
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(adminBulkTrash).toHaveBeenCalledWith('restore', [
      { type: 'file', id: 'f1' },
      { type: 'folder', id: 'd1' },
    ])
    expect(result.current.data).toEqual(response)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.adminTrash() })
  })

  it('purge action 전달', async () => {
    ;(adminBulkTrash as ReturnType<typeof vi.fn>).mockResolvedValue({ succeeded: [], failed: [] })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useAdminBulkTrash(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ action: 'purge', items: [{ type: 'file', id: 'f1' }] })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(adminBulkTrash).toHaveBeenCalledWith('purge', [{ type: 'file', id: 'f1' }])
  })

  it('202 APPROVAL_REQUIRED (purge) → ApprovalRequiredError + toast.info + invalidate 미호출', async () => {
    const apprErr = new ApprovalRequiredError(
      'aaaa-bbbb',
      '2026-05-15T00:00:00Z',
    )
    ;(adminBulkTrash as ReturnType<typeof vi.fn>).mockRejectedValue(apprErr)
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useAdminBulkTrash(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ action: 'purge', items: [{ type: 'file', id: 'f1' }] })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(ApprovalRequiredError)
    expect(invalidateSpy).not.toHaveBeenCalled()
    await waitFor(() => {
      const calls = toastSpy('info').mock.calls
      expect(calls.length).toBeGreaterThan(0)
      expect(calls[0][0]).toMatch(/휴지통 영구 삭제/)
    })
  })
})
