import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { BulkActionBar } from './BulkActionBar'
import { useSelectionStore } from '@/stores/selection'
import { useRenameUiStore } from '@/stores/renameUi'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useSortParams } from '@/hooks/useSortParams'
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

vi.mock('@/hooks/useDeleteBulk', () => ({
  useDeleteBulk: () => ({ mutate: vi.fn(), isPending: false }),
}))

const ITEMS: FileItem[] = [
  {
    id: 'f1',
    name: 'alpha.txt',
    type: 'file',
    mimeType: 'text/plain',
    size: 100,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
  {
    id: 'f2',
    name: 'beta.txt',
    type: 'file',
    mimeType: 'text/plain',
    size: 200,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
  {
    id: 'fo1',
    name: '폴더A',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  },
]

function makeWrapper() {
  const qc = new QueryClient()
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('BulkActionBar — 이름 변경 버튼', () => {
  beforeEach(() => {
    // selection / rename store 초기화
    act(() => {
      useSelectionStore.getState().clear()
      useRenameUiStore.getState().close()
    })
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: ITEMS,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    vi.mocked(useCurrentFolder).mockReturnValue({
      folderId: 'root',
      folder: { id: 'root', name: 'root', slugPath: [] },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useCurrentFolder>)
    vi.mocked(useSortParams).mockReturnValue({ sort: 'name', dir: 'asc' })
  })

  it('단일 선택 시 활성: 클릭하면 RenameDialog가 열린다 (id, name 전달)', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    expect(btn.getAttribute('title')).toBeNull()
    act(() => {
      fireEvent.click(btn)
    })
    const state = useRenameUiStore.getState()
    expect(state.isOpen).toBe(true)
    expect(state.targetId).toBe('f1')
    expect(state.targetName).toBe('alpha.txt')
  })

  it('다중 선택 시 비활성 + tooltip', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
      useSelectionStore.getState().toggle('f2')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    expect(btn.getAttribute('title')).toBe('단일 선택 시 사용 가능')
    // 클릭해도 다이얼로그가 열리지 않음 (disabled가 click 방지)
    act(() => {
      fireEvent.click(btn)
    })
    expect(useRenameUiStore.getState().isOpen).toBe(false)
  })

  it('단일 선택이지만 cache miss(items=undefined)면 비활성', () => {
    vi.mocked(useFilesInFolder).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useFilesInFolder>)
    act(() => {
      useSelectionStore.getState().selectOnly('f1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
  })

  it('단일 선택이 폴더여도 활성 (정책: 폴더 이름 변경 허용)', () => {
    act(() => {
      useSelectionStore.getState().selectOnly('fo1')
    })
    render(<BulkActionBar />, { wrapper: makeWrapper() })
    const btn = screen.getByRole('button', { name: '이름 변경' })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    act(() => {
      fireEvent.click(btn)
    })
    expect(useRenameUiStore.getState().targetId).toBe('fo1')
    expect(useRenameUiStore.getState().targetName).toBe('폴더A')
  })
})
