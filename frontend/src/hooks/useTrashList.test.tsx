import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTrashList } from './useTrashList'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
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

const SCOPE = { scopeType: 'department' as const, scopeId: 'd1' }
const TEAM_SCOPE = { scopeType: 'team' as const, scopeId: 't1' }

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

  it('queryKey — scopeType+scopeId 포함 (team)', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(
      () => useTrashList({ scopeType: 'team', scopeId: 't1' }),
      { wrapper: wrap(qc) },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    // queryKey는 qk.trashList('team', 't1')과 동일해야 한다
    const expectedKey = qk.trashList('team', 't1')
    expect(result.current.data).toBeDefined()
    // 캐시에 올바른 키로 저장됐는지 확인
    const cached = qc.getQueryData(expectedKey)
    expect(cached).toBeDefined()
  })

  it('queryKey — type 필터 포함 시 trashList key 뒤에 type 추가', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(
      () => useTrashList({ scopeType: 'department', scopeId: 'd1', type: 'file' }),
      { wrapper: wrap(qc) },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const expectedKey = [...qk.trashList('department', 'd1'), 'file'] as const
    const cached = qc.getQueryData(expectedKey)
    expect(cached).toBeDefined()
  })

  it('queryFn — scopeType+scopeId+cursor+type api.getTrash에 전달', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [itemA, itemB],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(SCOPE), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getTrash).toHaveBeenCalledWith({
      scopeType: 'department',
      scopeId: 'd1',
      cursor: undefined,
      type: undefined,
    })
  })

  it('scopeId 빈 문자열 — enabled=false, fetch 차단', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(
      () => useTrashList({ scopeType: 'department', scopeId: '' }),
      { wrapper: wrap(qc) },
    )
    // enabled=false이므로 pending 상태 유지
    expect(result.current.isPending).toBe(true)
    expect(api.getTrash).not.toHaveBeenCalled()
  })

  it('빈 휴지통 — items=[] / nextCursor=null 처리', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(SCOPE), { wrapper: wrap(qc) })
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
    const { result } = renderHook(() => useTrashList(SCOPE), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const all = result.current.data?.pages.flatMap((p) => p.items) ?? []
    expect(all).toEqual([itemA, itemB])
    expect(result.current.hasNextPage).toBe(false)
    expect(api.getTrash).toHaveBeenCalledWith({
      scopeType: 'department',
      scopeId: 'd1',
      cursor: undefined,
      type: undefined,
    })
  })

  it('cursor로 다음 페이지 fetch — fetchNextPage 호출 후 cursor를 api에 전달', async () => {
    const mock = api.getTrash as ReturnType<typeof vi.fn>
    mock
      .mockResolvedValueOnce({ items: [itemA], nextCursor: 'cur-2' } satisfies TrashPage)
      .mockResolvedValueOnce({ items: [itemB], nextCursor: null } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(SCOPE), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)

    await act(async () => {
      await result.current.fetchNextPage()
    })

    await waitFor(() => expect(mock).toHaveBeenCalledTimes(2))
    expect(mock).toHaveBeenLastCalledWith({
      scopeType: 'department',
      scopeId: 'd1',
      cursor: 'cur-2',
      type: undefined,
    })
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
    const { result } = renderHook(
      () => useTrashList({ ...SCOPE, type: 'folder' }),
      { wrapper: wrap(qc) },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getTrash).toHaveBeenCalledWith({
      scopeType: 'department',
      scopeId: 'd1',
      cursor: undefined,
      type: 'folder',
    })
    const all = result.current.data?.pages.flatMap((p) => p.items) ?? []
    expect(all).toEqual([folderC])
  })

  it('type 변경 시 queryKey 분리 — 새 fetch 발생', async () => {
    const mock = api.getTrash as ReturnType<typeof vi.fn>
    mock.mockResolvedValue({ items: [], nextCursor: null } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { rerender } = renderHook(
      ({ t }: { t: 'file' | 'folder' | undefined }) =>
        useTrashList({ ...SCOPE, type: t }),
      {
        wrapper: wrap(qc),
        initialProps: { t: undefined as 'file' | 'folder' | undefined },
      },
    )
    await waitFor(() => expect(mock).toHaveBeenCalledTimes(1))
    rerender({ t: 'file' })
    await waitFor(() => expect(mock).toHaveBeenCalledTimes(2))
    expect(mock).toHaveBeenLastCalledWith({
      scopeType: 'department',
      scopeId: 'd1',
      cursor: undefined,
      type: 'file',
    })
  })

  it('team scope — queryKey + api 호출 모두 team으로', async () => {
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [],
      nextCursor: null,
    } satisfies TrashPage)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(TEAM_SCOPE), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getTrash).toHaveBeenCalledWith({
      scopeType: 'team',
      scopeId: 't1',
      cursor: undefined,
      type: undefined,
    })
    const cached = qc.getQueryData(qk.trashList('team', 't1'))
    expect(cached).toBeDefined()
  })

  it('401 에러 — isError=true (인증 만료 surface)', async () => {
    const err = Object.assign(new Error('getTrash failed: 401'), { status: 401 })
    ;(api.getTrash as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTrashList(SCOPE), { wrapper: wrap(qc) })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { status?: number })?.status).toBe(401)
  })
})
