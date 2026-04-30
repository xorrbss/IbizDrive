import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { FileTable } from './FileTable'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useViewParam } from '@/hooks/useViewParam'
import { useSortParams } from '@/hooks/useSortParams'
import type { FileItem } from '@/types/file'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(''),
}))
vi.mock('@/hooks/useFilesInFolder', () => ({ useFilesInFolder: vi.fn() }))
vi.mock('@/hooks/useViewParam', () => ({ useViewParam: vi.fn() }))
vi.mock('@/hooks/useSortParams', () => ({ useSortParams: vi.fn() }))
vi.mock('@/hooks/useNativeFileDrop', () => ({ useNativeFileDrop: () => false }))
vi.mock('@/hooks/useUpload', () => ({ useUpload: () => ({ enqueue: vi.fn() }) }))
vi.mock('@/hooks/useDeleteBulk', () => ({ useDeleteBulk: () => ({ mutate: vi.fn() }) }))

const ITEMS: FileItem[] = [
  {
    id: 'f1',
    name: 'a.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 1000,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
  {
    id: 'f2',
    name: 'b.txt',
    type: 'file',
    mimeType: 'text/plain',
    size: 2000,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
]

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

beforeEach(() => {
  vi.mocked(useFilesInFolder).mockReturnValue({
    data: ITEMS,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useFilesInFolder>)
  vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc', setSort: vi.fn() })
})

describe('FileTable view 분기 (M16.2)', () => {
  it('view=list (default) — 헤더(이름/크기/수정일/수정자) 컬럼 렌더', () => {
    vi.mocked(useViewParam).mockReturnValue({ view: 'list', setView: vi.fn() })
    render(wrap(<FileTable folderId="root" />))
    expect(screen.getByRole('grid', { name: '파일 목록' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: '이름' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: '크기' })).toBeTruthy()
  })

  it('view=grid — 컬럼 헤더 없음 + FileCard 렌더 (gridcell 항목 수)', () => {
    vi.mocked(useViewParam).mockReturnValue({ view: 'grid', setView: vi.fn() })
    render(wrap(<FileTable folderId="root" />))
    expect(screen.getByRole('grid', { name: '파일 그리드' })).toBeTruthy()
    expect(screen.queryByRole('columnheader')).toBeNull()
    // FileCard renders role=gridcell — 2 items
    expect(screen.getAllByRole('gridcell').length).toBe(2)
    expect(screen.getByText('a.pdf')).toBeTruthy()
    expect(screen.getByText('b.txt')).toBeTruthy()
  })
})
