import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { FileTable } from './FileTable'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useViewParam } from '@/hooks/useViewParam'
import { useSortParams } from '@/hooks/useSortParams'
import { useGridColumns } from '@/hooks/useGridColumns'
import { useSelectionStore } from '@/stores/selection'
import type { FileItem } from '@/types/file'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(''),
}))
vi.mock('@/hooks/useFilesInFolder', () => ({ useFilesInFolder: vi.fn() }))
vi.mock('@/hooks/useViewParam', () => ({ useViewParam: vi.fn() }))
vi.mock('@/hooks/useSortParams', () => ({ useSortParams: vi.fn() }))
vi.mock('@/hooks/useGridColumns', () => ({ useGridColumns: vi.fn(() => 1) }))
vi.mock('@/hooks/useNativeFileDrop', () => ({ useNativeFileDrop: () => false }))
vi.mock('@/hooks/useUpload', () => ({ useUpload: () => ({ enqueue: vi.fn() }) }))
vi.mock('@/hooks/useDeleteBulk', () => ({ useDeleteBulk: () => ({ mutate: vi.fn() }) }))

// jsdom 한계: 가상화 컨테이너의 clientHeight=0 → virtualizer가 빈 viewport로 판단해
// virtualItems 미반환. 테스트에서는 list/grid 분기 렌더 자체를 검증해야 하므로 virtualizer를
// "전 항목을 visible로 반환"하도록 mock. M16V 추가 — list/grid 두 분기 모두에 적용.
vi.mock('@tanstack/react-virtual', () => ({
  useVirtualizer: ({ count, estimateSize }: { count: number; estimateSize: () => number }) => {
    const size = estimateSize()
    return {
      getTotalSize: () => count * size,
      getVirtualItems: () =>
        Array.from({ length: count }, (_, i) => ({
          index: i,
          start: i * size,
          size,
          end: (i + 1) * size,
          key: i,
          lane: 0,
        })),
      scrollToIndex: vi.fn(),
    }
  },
}))

// jsdom에는 ResizeObserver 없음 → useGridColumns가 columns=1로 초기 상태 유지.
// 본 분기 회귀 테스트에서는 columns 정확값보다 렌더 마커가 핵심.
class FakeResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
;(globalThis as unknown as { ResizeObserver: typeof FakeResizeObserver }).ResizeObserver =
  FakeResizeObserver

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

  it('view=grid — data-grid-virtual 마커 존재 (M16V 가상화)', () => {
    vi.mocked(useViewParam).mockReturnValue({ view: 'grid', setView: vi.fn() })
    render(wrap(<FileTable folderId="root" />))
    const grid = screen.getByRole('grid', { name: '파일 그리드' })
    expect(grid.getAttribute('data-grid-virtual')).toBe('true')
  })
})

// ─── 디자인 핸드오프 G4 — FileTable 6열 (체크박스 + action col) ───
describe('FileTable G4 — 6열 체크박스/action 시각화', () => {
  beforeEach(() => {
    // selection store 격리
    useSelectionStore.setState({
      ids: new Set(),
      lastClickedId: null,
      pendingIds: new Set(),
    })
    vi.mocked(useViewParam).mockReturnValue({ view: 'list', setView: vi.fn() })
  })

  it('row 당 checkbox 버튼 노출 (aria-checked + name 라벨)', () => {
    render(wrap(<FileTable folderId="root" />))
    const cb1 = screen.getByRole('checkbox', { name: 'a.pdf 선택' })
    const cb2 = screen.getByRole('checkbox', { name: 'b.txt 선택' })
    expect(cb1.getAttribute('aria-checked')).toBe('false')
    expect(cb2.getAttribute('aria-checked')).toBe('false')
  })

  it('checkbox 클릭 시 selection store 토글', () => {
    render(wrap(<FileTable folderId="root" />))
    const cb1 = screen.getByRole('checkbox', { name: 'a.pdf 선택' })
    fireEvent.click(cb1)
    expect(useSelectionStore.getState().ids.has('f1')).toBe(true)
    fireEvent.click(cb1)
    expect(useSelectionStore.getState().ids.has('f1')).toBe(false)
  })

  it('row 당 "더 보기" 버튼 노출 (액션 컬럼 layout placeholder)', () => {
    render(wrap(<FileTable folderId="root" />))
    expect(screen.getAllByRole('button', { name: '더 보기' })).toHaveLength(2)
  })
})

// M16VK Grid 2D 키보드 wrap — useGridColumns mock 변동 + 6 items 사용.
describe('FileTable Grid 2D 키보드 wrap (M16VK)', () => {
  const SIX_ITEMS: FileItem[] = Array.from({ length: 6 }, (_, i) => ({
    id: `g${i}`,
    name: `g${i}.txt`,
    type: 'file',
    mimeType: 'text/plain',
    size: 100,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  }))

  beforeEach(() => {
    vi.mocked(useViewParam).mockReturnValue({ view: 'grid', setView: vi.fn() })
    vi.mocked(useGridColumns).mockReturnValue(3) // 3 cols × 2 rows
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: SIX_ITEMS,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useFilesInFolder>)
  })

  const focusedId = (): string | null => {
    const cells = screen.getAllByRole('gridcell')
    const focused = cells.find((c) => c.getAttribute('tabIndex') === '0')
    return focused?.getAttribute('data-file-id') ?? null
  }

  it('ArrowDown moves by columns (g0 → g3)', () => {
    render(wrap(<FileTable folderId="root" />))
    const grid = screen.getByRole('grid', { name: '파일 그리드' })
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // -1 → 0
    expect(focusedId()).toBe('g0')
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // 0 → 3 (columns step)
    expect(focusedId()).toBe('g3')
  })

  it('ArrowRight moves by 1 (g0 → g1)', () => {
    render(wrap(<FileTable folderId="root" />))
    const grid = screen.getByRole('grid', { name: '파일 그리드' })
    fireEvent.keyDown(grid, { key: 'ArrowDown' })
    expect(focusedId()).toBe('g0')
    fireEvent.keyDown(grid, { key: 'ArrowRight' })
    expect(focusedId()).toBe('g1')
  })

  it('ArrowRight wraps across row boundary (g2 → g3)', () => {
    render(wrap(<FileTable folderId="root" />))
    const grid = screen.getByRole('grid', { name: '파일 그리드' })
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // → g0
    fireEvent.keyDown(grid, { key: 'ArrowRight' }) // → g1
    fireEvent.keyDown(grid, { key: 'ArrowRight' }) // → g2 (end of row 0)
    fireEvent.keyDown(grid, { key: 'ArrowRight' }) // → g3 (wrap to row 1)
    expect(focusedId()).toBe('g3')
  })

  it('ArrowLeft wraps backwards (g3 → g2)', () => {
    render(wrap(<FileTable folderId="root" />))
    const grid = screen.getByRole('grid', { name: '파일 그리드' })
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // → g0
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // → g3
    fireEvent.keyDown(grid, { key: 'ArrowLeft' }) // → g2 (wrap to row 0)
    expect(focusedId()).toBe('g2')
  })

  it('ArrowUp moves by columns (g4 → g1)', () => {
    render(wrap(<FileTable folderId="root" />))
    const grid = screen.getByRole('grid', { name: '파일 그리드' })
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // g0
    fireEvent.keyDown(grid, { key: 'ArrowRight' }) // g1
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // g4
    expect(focusedId()).toBe('g4')
    fireEvent.keyDown(grid, { key: 'ArrowUp' }) // g4 - 3 = g1
    expect(focusedId()).toBe('g1')
  })

  it('ArrowDown stays on last row (no further row)', () => {
    render(wrap(<FileTable folderId="root" />))
    const grid = screen.getByRole('grid', { name: '파일 그리드' })
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // g0
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // g3
    fireEvent.keyDown(grid, { key: 'ArrowDown' }) // overshoot — exact row, no further
    expect(focusedId()).toBe('g3')
  })
})
