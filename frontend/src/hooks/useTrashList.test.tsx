import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTrashList } from './useTrashList'
import { api } from '@/lib/api'
import type { TrashItem, TrashPage } from '@/types/trash'

vi.mock('@/lib/api', () => ({
  api: { getTrash: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const itemA: TrashItem = {
  id: 'f1',
  name: 'a.pdf',
  type: 'file',
  deletedAt: '2026-04-30T10:00:00Z',
  purgeAfter: '2026-05-30T10:00:00Z',
  originalParentId: 'p1',
}
const itemB: TrashItem = {
  id: 'f2',
  name: 'b.pdf',
  type: 'file',
  deletedAt: '2026-04-30T11:00:00Z',
  purgeAfter: '2026-05-30T11:00:00Z',
  originalParentId: 'p1',
}
const folderC: TrashItem = {
  id: 'd1',
  name: 'C',
  type: 'folder',
  deletedAt: '2026-04-30T12:00:00Z',
  purgeAfter: '2026-05-30T12:00:00Z',
  originalParentId: null,
}

describe('useTrashList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('빈 휴지통 — items=[] / nextCursor=null 처리', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const all = result.current.data?.pages.flatMap((p) => p.items) ?? []
    expect(all).toEqual([])
    expect(result.current.hasNextPage).toBe(false)
  })

  it('한 페이지 — items 반환 + hasNextPage=false', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [itemA, itemB],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const all = result.current.data?.pages.flatMap((p) => p.items) ?? []
    expect(all).toEqual([itemA, itemB])
    expect(result.current.hasNextPage).toBe(false)
    expect(api.getTrash).toHaveBeenCalledWith({ cursor: undefined, type: undefined })
  })

  it('cursor로 다음 페이지 fetch — fetchNextPage 호출 후 cursor를 api에 전달', async () => {
    const mock = api.getTrash as ReturnType<typeof vi.fn>
    mock
      .mockResolvedValueOnce({ items: [itemA], nextCursor: 'cur-2' } satisfies TrashPage)
      .mockResolvedValueOnce({ items: [itemB], nextCursor: null } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)

    await act(async () => {
      await result.current.fetchNextPage()
    })

    await waitFor(() => expect(mock).toHaveBeenCalledTimes(2))
    expect(mock).toHaveBeenLastCalledWith({ cursor: 'cur-2', type: undefined })
    const all = result.current.data?.pages.flatMap((p) => p.items) ?? []
    expect(all).toEqual([itemA, itemB])
    expect(result.current.hasNextPage).toBe(false)
  })

  it('type 필터 — folder만 요청', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [folderC],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList({ type: 'folder' }), {
      wrapper: wrap(qc),
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getTrash).toHaveBeenCalledWith({ cursor: undefined, type: 'folder' })
    const all = result.current.data?.pages.flatMap((p) => p.items) ?? []
    expect(all).toEqual([folderC])
  })

  it('type 변경 시 queryKey 분리 — 새 fetch 발생', async () => {
    const mock = api.getTrash as ReturnType<typeof vi.fn>
    mock.mockResolvedValue({ items: [], nextCursor: null } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { rerender } = renderHook(
      ({ t }: { t: 'file' | 'folder' | undefined }) => useTrashList({ type: t }),
      {
        wrapper: wrap(qc),
        initialProps: { t: undefined as 'file' | 'folder' | undefined },
      },
    )
    await waitFor(() => expect(mock).toHaveBeenCalledTimes(1))
    rerender({ t: 'file' })
    await waitFor(() => expect(mock).toHaveBeenCalledTimes(2))
    expect(mock).toHaveBeenLastCalledWith({ cursor: undefined, type: 'file' })
  })

  it('401 에러 — isError=true (인증 만료 surface)', async () => {
    const err = Object.assign(new Error('getTrash failed: 401'), { status: 401 })
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { status?: number })?.status).toBe(401)
  })
})
