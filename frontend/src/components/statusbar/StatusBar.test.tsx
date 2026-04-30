import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { StatusBar } from './StatusBar'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useSelectionStore } from '@/stores/selection'
import type { FileItem } from '@/types/file'

vi.mock('@/hooks/useFilesInFolder', () => ({
  useFilesInFolder: vi.fn(),
}))
vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: vi.fn(),
}))
vi.mock('@/hooks/useSortParams', () => ({
  useSortParams: vi.fn(),
}))

const ITEMS: FileItem[] = Array.from({ length: 7 }, (_, i) => ({
  id: `f${i}`,
  name: `f${i}.txt`,
  type: 'file',
  mimeType: 'text/plain',
  size: 100,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
}))

beforeEach(() => {
  vi.mocked(useFilesInFolder).mockReturnValue({
    data: ITEMS,
    isLoading: false,
    error: null,
  } as ReturnType<typeof useFilesInFolder>)
  vi.mocked(useCurrentFolder).mockReturnValue({
    folderId: 'root',
    folder: { id: 'root', name: 'root', slugPath: [] },
    breadcrumb: [],
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof useCurrentFolder>)
  vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc' })
  act(() => useSelectionStore.getState().clear())
})

describe('StatusBar (M14)', () => {
  it('contentinfo 푸터 + 항목 수 표시', () => {
    render(<StatusBar />)
    const footer = screen.getByRole('contentinfo')
    expect(footer.textContent).toMatch(/항목/)
    expect(footer.textContent).toMatch(/7/)
  })

  it('selection 0 — 선택 카운트 미표시', () => {
    render(<StatusBar />)
    expect(screen.queryByText(/선택됨/)).toBeNull()
  })

  it('selection 변동 → 선택 카운트 표시 (aria-live=polite)', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
      useSelectionStore.getState().toggle('f2')
    })
    render(<StatusBar />)
    expect(screen.getByText(/선택됨/).textContent).toMatch(/2/)
  })

  it('빈 폴더 (data 없음) — 0개 표시', () => {
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    render(<StatusBar />)
    expect(screen.getByRole('contentinfo').textContent).toMatch(/0/)
  })
})
