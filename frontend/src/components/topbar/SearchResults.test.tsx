import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SearchResults } from './SearchResults'
import type { FileItem } from '@/types/file'

const mockOpen = vi.fn()
const mockPush = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(''),
}))

vi.mock('@/hooks/useOpenFile', () => ({
  useOpenFile: () => ({ open: mockOpen, close: vi.fn(), fileId: null }),
}))

const fileItem: FileItem = {
  id: 'file_x',
  name: 'x.pdf',
  type: 'file',
  mimeType: 'application/pdf',
  size: 100,
  updatedAt: '2026-04-30T00:00:00Z',
  updatedBy: 'tester',
  parentId: 'root',
}

const folderItem: FileItem = {
  id: 'folder_y',
  name: 'Y폴더',
  type: 'folder',
  mimeType: null,
  size: null,
  updatedAt: '2026-04-30T00:00:00Z',
  updatedBy: 'tester',
  parentId: 'root',
}

describe('SearchResults', () => {
  beforeEach(() => {
    mockOpen.mockClear()
    mockPush.mockClear()
  })

  it('query 1자 → "2자 이상" 안내', () => {
    render(<SearchResults query="가" isFetching={false} isError={false} items={undefined} />)
    expect(screen.getByText(/2자 이상 입력하세요/)).toBeTruthy()
  })

  it('isError → 에러 메시지', () => {
    render(<SearchResults query="ab" isFetching={false} isError items={undefined} />)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('isFetching이고 items 없음 → "검색 중…"', () => {
    render(<SearchResults query="ab" isFetching isError={false} items={undefined} />)
    expect(screen.getByText(/검색 중/)).toBeTruthy()
  })

  it('items 빈 배열 → "결과가 없습니다"', () => {
    render(<SearchResults query="ab" isFetching={false} isError={false} items={[]} />)
    expect(screen.getByText(/결과가 없습니다/)).toBeTruthy()
  })

  it('파일 결과 클릭 → useOpenFile.open 호출', () => {
    render(<SearchResults query="ab" isFetching={false} isError={false} items={[fileItem]} />)
    fireEvent.click(screen.getByRole('button', { name: /x\.pdf/ }))
    expect(mockOpen).toHaveBeenCalledWith('file_x')
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('폴더 결과 클릭 → router.push(canonical path)', () => {
    render(<SearchResults query="ab" isFetching={false} isError={false} items={[folderItem]} />)
    fireEvent.click(screen.getByRole('button', { name: /Y폴더/ }))
    expect(mockPush).toHaveBeenCalledWith('/files/folder_y')
    expect(mockOpen).not.toHaveBeenCalled()
  })

  it('onSelect 콜백 — 결과 클릭 시 호출', () => {
    const onSelect = vi.fn()
    render(
      <SearchResults
        query="ab"
        isFetching={false}
        isError={false}
        items={[fileItem]}
        onSelect={onSelect}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /x\.pdf/ }))
    expect(onSelect).toHaveBeenCalled()
  })
})
