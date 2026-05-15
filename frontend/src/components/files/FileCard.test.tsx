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

  it('selected 상태 — ring-1 accent 클래스 + aria-selected=true', () => {
    // zip styles.css `.grid-card.selected { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent) }`.
    // Tailwind 표현: `border-accent ring-1 ring-accent` (background 변화 없음, 1px outline).
    render(
      <FileCard
        item={ITEM}
        isFocused={false}
        isSelected={true}
        isPending={false}
      />,
    )
    const cell = screen.getByRole('gridcell')
    expect(cell.className).toMatch(/ring-1/)
    expect(cell.className).toMatch(/ring-accent/)
    expect(cell.className).toMatch(/border-accent/)
    expect(cell.getAttribute('aria-selected')).toBe('true')
  })

  it('opened 상태 — selected와 동일한 accent ring', () => {
    // RightPanel(`?file=`)에 열린 카드. zip은 `.grid-card.opened` 미정의이나
    // selected와 동일 시각(border + ring accent) 적용 — FileRow.opened가 inset 2px와 다른 이유:
    // grid 카드는 좌측 inset border가 불가(border-radius 깨짐). 1px ring으로 통일.
    render(
      <FileCard
        item={ITEM}
        isFocused={false}
        isSelected={false}
        isPending={false}
        isOpened={true}
      />,
    )
    const cell = screen.getByRole('gridcell')
    expect(cell.className).toMatch(/ring-1/)
    expect(cell.className).toMatch(/ring-accent/)
  })

  it('starred=true — grid-star 배지 (top-right) 노출, false 시 미노출', () => {
    // zip styles.css `.grid-star { position: absolute; top: 6px; right: 6px; color: var(--warn); ... }`
    // (L744~753). list view FileRow:178~184 와 동일하게 starred 자료가 있을 때만 렌더.
    const { rerender } = render(
      <FileCard
        item={{ ...ITEM, starred: true }}
        isFocused={false}
        isSelected={false}
        isPending={false}
      />,
    )
    const badge = screen.getByLabelText('즐겨찾기')
    expect(badge.className).toMatch(/absolute/)
    expect(badge.className).toMatch(/text-warn/)

    rerender(
      <FileCard
        item={ITEM}
        isFocused={false}
        isSelected={false}
        isPending={false}
      />,
    )
    expect(screen.queryByLabelText('즐겨찾기')).toBeNull()
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
