import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { useRef } from 'react'
import { FileGrid } from './FileGrid'
import type { FileItem } from '@/types/file'

const mkFile = (id: string, name: string): FileItem => ({
  id,
  name,
  type: 'file',
  mimeType: 'application/pdf',
  size: 100,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: '나',
  parentId: 'root',
})

function Wrapper(props: {
  items: FileItem[]
  selectedIds?: Set<string>
  pendingIds?: Set<string>
  focusedIndex?: number
  onClick?: (item: FileItem, e: React.MouseEvent) => void
  onDoubleClick?: (item: FileItem) => void
  onKeyDown?: (e: React.KeyboardEvent) => void
}) {
  const ref = useRef<HTMLDivElement>(null)
  return (
    <FileGrid
      items={props.items}
      focusedIndex={props.focusedIndex ?? -1}
      selectedIds={props.selectedIds ?? new Set()}
      pendingIds={props.pendingIds ?? new Set()}
      onClick={props.onClick ?? (() => {})}
      onDoubleClick={props.onDoubleClick ?? (() => {})}
      onKeyDown={props.onKeyDown ?? (() => {})}
      scrollRef={ref}
    />
  )
}

describe('FileGrid', () => {
  it('items 개수만큼 카드 렌더', () => {
    render(
      <Wrapper
        items={[mkFile('f1', 'a.pdf'), mkFile('f2', 'b.pdf'), mkFile('f3', 'c.pdf')]}
      />,
    )
    expect(screen.getByText('a.pdf')).toBeTruthy()
    expect(screen.getByText('b.pdf')).toBeTruthy()
    expect(screen.getByText('c.pdf')).toBeTruthy()
    expect(screen.getAllByRole('gridcell')).toHaveLength(3)
  })

  it('카드 클릭 → onClick 호출', () => {
    const onClick = vi.fn()
    render(<Wrapper items={[mkFile('f1', 'a.pdf')]} onClick={onClick} />)
    fireEvent.click(screen.getByLabelText('a.pdf'))
    expect(onClick).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'f1' }),
      expect.anything(),
    )
  })

  it('isSelected → aria-selected=true', () => {
    render(
      <Wrapper
        items={[mkFile('f1', 'a.pdf')]}
        selectedIds={new Set(['f1'])}
      />,
    )
    expect(screen.getByLabelText('a.pdf').getAttribute('aria-selected')).toBe('true')
  })

  it('isPending → aria-disabled=true + 클릭 무시', () => {
    const onClick = vi.fn()
    render(
      <Wrapper
        items={[mkFile('f1', 'a.pdf')]}
        pendingIds={new Set(['f1'])}
        onClick={onClick}
      />,
    )
    expect(screen.getByLabelText('a.pdf').getAttribute('aria-disabled')).toBe('true')
    fireEvent.click(screen.getByLabelText('a.pdf'))
    expect(onClick).not.toHaveBeenCalled()
  })
})
