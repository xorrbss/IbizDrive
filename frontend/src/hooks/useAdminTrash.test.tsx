import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import {
  useAdminTrashList,
  useAdminRestoreTrashItem,
  useAdminPurgeTrashItem,
} from './useAdminTrash'
import { api, adminListTrash } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AdminTrashFilters, AdminTrashPage } from '@/types/trash'

vi.mock('@/lib/api', () => ({
  adminListTrash: vi.fn(),
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
