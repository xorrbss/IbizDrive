import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { FileCard } from './FileCard'
import type { FileItem } from '@/types/file'

const ITEM: FileItem = {
  id: 'file_a',
  name: '제안서.pdf',
  type: 'file',
  mimeType: 'application/pdf',
  size: 2_400_000,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
}

const FOLDER: FileItem = {
  id: 'folder_x',
  name: '영업팀',
  type: 'folder',
  mimeType: null,
  size: null,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
}

describe('FileCard (M16.1)', () => {
  it('이름 + 메타(크기) 표시', () => {
    render(
      <FileCard
        item={ITEM}
        isFocused={false}
        isSelected={false}
        isPending={false}
      />,
    )
    expect(screen.getByText('제안서.pdf')).toBeTruthy()
    expect(screen.getByText(/2\.3 MB/)).toBeTruthy()
  })

  it('폴더 — "폴더" 메타 표시', () => {
    render(
      <FileCard
        item={FOLDER}
        isFocused={false}
        isSelected={false}
        isPending={false}
      />,
    )
    expect(screen.getByText('영업팀')).toBeTruthy()
    expect(screen.getByText('폴더')).toBeTruthy()
  })

  it('클릭 / 더블클릭 콜백 호출', () => {
    const onClick = vi.fn()
    const onDoubleClick = vi.fn()
    render(
      <FileCard
        item={ITEM}
        isFocused={false}
        isSelected={false}
        isPending={false}
        onClick={onClick}
        onDoubleClick={onDoubleClick}
      />,
    )
    const cell = screen.getByRole('gridcell')
    fireEvent.click(cell)
    fireEvent.doubleClick(cell)
    expect(onClick).toHaveBeenCalledTimes(1)
    expect(onDoubleClick).toHaveBeenCalledTimes(1)
  })

  it('selected 상태 — ring-accent 클래스 + aria-selected=true', () => {
    render(
      <FileCard
        item={ITEM}
        isFocused={false}
        isSelected={true}
        isPending={false}
      />,
    )
    const cell = screen.getByRole('gridcell')
    expect(cell.className).toMatch(/ring-2/)
    expect(cell.className).toMatch(/ring-accent/)
    expect(cell.getAttribute('aria-selected')).toBe('true')
  })

  it('pending 상태 — 클릭 무시 + aria-disabled', () => {
    const onClick = vi.fn()
    render(
      <FileCard
        item={ITEM}
        isFocused={false}
        isSelected={false}
        isPending={true}
        onClick={onClick}
      />,
    )
    const cell = screen.getByRole('gridcell')
    fireEvent.click(cell)
    expect(onClick).not.toHaveBeenCalled()
    expect(cell.getAttribute('aria-disabled')).toBe('true')
  })
})
