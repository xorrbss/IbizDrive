import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TrashTable } from './TrashTable'
import { useTrashList } from '@/hooks/useTrashList'
import { usePermission } from '@/hooks/usePermission'
import type { TrashItem } from '@/types/trash'

vi.mock('@/hooks/useTrashList', () => ({ useTrashList: vi.fn() }))
vi.mock('@/hooks/usePermission', () => ({ usePermission: vi.fn() }))
vi.mock('@/hooks/useRestoreItem', () => ({
  useRestoreItem: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/hooks/usePurgeTrashItem', () => ({
  usePurgeTrashItem: () => ({ mutate: vi.fn(), isPending: false }),
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const itemFile: TrashItem = {
  id: 'f1',
  name: '제안서.pdf',
  type: 'file',
  deletedAt: '2026-04-30T10:00:00Z',
  purgeAfter: '2026-05-30T10:00:00Z',
  originalParentId: 'p1',
}
const itemFolder: TrashItem = {
  id: 'd1',
  name: '계약서',
  type: 'folder',
  deletedAt: '2026-04-30T11:00:00Z',
  purgeAfter: '2026-05-30T11:00:00Z',
  originalParentId: null,
}

// mockTree kept for reference; TrashTable now uses tree=undefined (Tasks 17+ 대기).
// originalParentId가 있어도 "원위치 폴더 삭제됨" 폴백으로 표시됨.
const _mockTree = {
  id: 'root',
  parentId: null,
  name: 'root',
  slug: '',
  children: [
    { id: 'p1', parentId: 'root', name: '영업팀', slug: '영업팀', children: [] },
  ],
}

function setHook(opts: {
  isLoading?: boolean
  isError?: boolean
  items?: TrashItem[]
  hasNextPage?: boolean
}) {
  ;(useTrashList as ReturnType<typeof vi.fn>).mockReturnValue({
    isLoading: opts.isLoading ?? false,
    isError: opts.isError ?? false,
    data: opts.items
      ? { pages: [{ items: opts.items, nextCursor: null }] }
      : undefined,
    hasNextPage: opts.hasNextPage ?? false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  })
}

describe('TrashTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue({ PURGE: true })
  })

  it('isLoading → 로딩 상태', () => {
    setHook({ isLoading: true })
    const qc = new QueryClient()
    render(<TrashTable scopeType="department" scopeId="d1" />, { wrapper: wrap(qc) })
    expect(screen.getByText('로딩…')).toBeTruthy()
  })

  it('isError → 에러 alert', () => {
    setHook({ isError: true })
    const qc = new QueryClient()
    render(<TrashTable scopeType="department" scopeId="d1" />, { wrapper: wrap(qc) })
    expect(screen.getByRole('alert').textContent).toMatch(/불러올 수 없습니다/)
  })

  it('items가 비어있으면 Empty 메시지', () => {
    setHook({ items: [] })
    const qc = new QueryClient()
    render(<TrashTable scopeType="department" scopeId="d1" />, { wrapper: wrap(qc) })
    expect(screen.getByText('휴지통이 비어있습니다')).toBeTruthy()
  })

  it('items 렌더 + aria-rowcount (tree=undefined → path 폴백)', () => {
    setHook({ items: [itemFile, itemFolder] })
    const qc = new QueryClient()
    render(<TrashTable scopeType="department" scopeId="d1" />, { wrapper: wrap(qc) })
    const grid = screen.getByRole('grid', { name: '휴지통 항목' })
    // header(1) + rows(2)
    expect(grid.getAttribute('aria-rowcount')).toBe('3')
    expect(screen.getByText('제안서.pdf')).toBeTruthy()
    expect(screen.getByText('계약서')).toBeTruthy()
    // tree=undefined → originalParentId가 있으면 "원위치 폴더 삭제됨" (Tasks 17+ 대기)
    expect(screen.getByText('원위치 폴더 삭제됨')).toBeTruthy()
    // folder는 originalParentId=null → '최상위'
    expect(screen.getByText('최상위')).toBeTruthy()
  })

  it('originalParentId가 있으면 tree=undefined → "원위치 폴더 삭제됨" 폴백', () => {
    const orphan: TrashItem = { ...itemFile, id: 'f2', name: '고아.pdf', originalParentId: 'gone' }
    setHook({ items: [orphan] })
    const qc = new QueryClient()
    render(<TrashTable scopeType="department" scopeId="d1" />, { wrapper: wrap(qc) })
    expect(screen.getByText('원위치 폴더 삭제됨')).toBeTruthy()
  })

  it('ADMIN → 영구 삭제 버튼 표시', () => {
    setHook({ items: [itemFile] })
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue({ PURGE: true })
    const qc = new QueryClient()
    render(<TrashTable scopeType="department" scopeId="d1" />, { wrapper: wrap(qc) })
    expect(screen.getByRole('button', { name: '영구 삭제' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '복원' })).toBeTruthy()
  })

  it('non-ADMIN → 영구 삭제 버튼 숨김 (복원만)', () => {
    setHook({ items: [itemFile] })
    ;(usePermission as ReturnType<typeof vi.fn>).mockReturnValue({ PURGE: false })
    const qc = new QueryClient()
    render(<TrashTable scopeType="department" scopeId="d1" />, { wrapper: wrap(qc) })
    expect(screen.queryByRole('button', { name: '영구 삭제' })).toBeNull()
    expect(screen.getByRole('button', { name: '복원' })).toBeTruthy()
  })
})
