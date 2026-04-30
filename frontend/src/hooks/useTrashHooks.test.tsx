import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTrashList } from './useTrashList'
import { useRestoreBulk } from './useRestoreBulk'
import { usePurgeBulk } from './usePurgeBulk'
import { api } from '@/lib/api'
import type { FileItem } from '@/types/file'

function wrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'TrashTestWrapper'
  return Wrapper
}

function freshClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

const sample = (id: string, deletedAt: string | null = null): FileItem => ({
  id,
  name: `${id}.txt`,
  type: 'file',
  mimeType: 'text/plain',
  size: 10,
  updatedAt: '2026-04-29T00:00:00Z',
  updatedBy: 'tester',
  parentId: 'root',
  deletedAt,
  originalParentId: deletedAt ? 'root' : null,
})

describe('useTrashList', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listTrash').mockResolvedValue({
      items: [sample('a', '2026-04-29T00:00:01Z'), sample('b', '2026-04-29T00:00:00Z')],
    })
  })
  afterEach(() => vi.restoreAllMocks())

  it('items 반환', async () => {
    const { result } = renderHook(() => useTrashList(), { wrapper: wrapper(freshClient()) })
    await waitFor(() => expect(result.current.data).toBeDefined())
    expect(result.current.data?.items.map((i) => i.id)).toEqual(['a', 'b'])
  })
})

describe('useRestoreBulk', () => {
  beforeEach(() => {
    vi.spyOn(api, 'restoreBulk').mockResolvedValue({ restoredIds: ['a', 'b'] })
  })
  afterEach(() => vi.restoreAllMocks())

  it('mutate 후 onSuccess 호출 + api.restoreBulk 호출', async () => {
    const onSuccess = vi.fn()
    const qc = freshClient()
    const { result } = renderHook(() => useRestoreBulk({ onSuccess }), {
      wrapper: wrapper(qc),
    })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], originalParentIds: ['root'] })
    })
    await waitFor(() => expect(onSuccess).toHaveBeenCalled())
    expect(api.restoreBulk).toHaveBeenCalledWith(['a', 'b'])
  })

  it('onError — api 실패 시 호출', async () => {
    vi.spyOn(api, 'restoreBulk').mockRejectedValueOnce(new Error('fail'))
    const onError = vi.fn()
    const { result } = renderHook(() => useRestoreBulk({ onError }), {
      wrapper: wrapper(freshClient()),
    })
    await act(async () => {
      result.current.mutate({ ids: ['x'] })
    })
    await waitFor(() => expect(onError).toHaveBeenCalled())
  })
})

describe('usePurgeBulk', () => {
  beforeEach(() => {
    vi.spyOn(api, 'purgeBulk').mockResolvedValue({ purgedIds: ['a'] })
  })
  afterEach(() => vi.restoreAllMocks())

  it('mutate 후 onSuccess + api.purgeBulk 호출', async () => {
    const onSuccess = vi.fn()
    const { result } = renderHook(() => usePurgeBulk({ onSuccess }), {
      wrapper: wrapper(freshClient()),
    })
    await act(async () => {
      result.current.mutate({ ids: ['a'] })
    })
    await waitFor(() => expect(onSuccess).toHaveBeenCalled())
    expect(api.purgeBulk).toHaveBeenCalledWith(['a'])
  })
})
