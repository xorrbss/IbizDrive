import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { FilterPopover } from './FilterPopover'
import { useFileFiltersStore } from '@/stores/fileFilters'

describe('FilterPopover', () => {
  beforeEach(() => {
    useFileFiltersStore.getState().reset()
  })

  it('kind chip 클릭 → toggleKind 호출 (store 갱신)', () => {
    render(<FilterPopover onClose={() => {}} />)
    fireEvent.click(screen.getByRole('button', { name: 'PDF' }))
    expect(useFileFiltersStore.getState().filters.kinds).toEqual(['pdf'])
    fireEvent.click(screen.getByRole('button', { name: /PDF/ }))
    expect(useFileFiltersStore.getState().filters.kinds).toEqual([])
  })

  it('modified radio — 7일 선택 시 store 갱신', () => {
    render(<FilterPopover onClose={() => {}} />)
    fireEvent.click(screen.getByText('최근 7일'))
    expect(useFileFiltersStore.getState().filters.modified).toBe('7d')
  })

  it('starred 체크박스 — store 갱신', () => {
    render(<FilterPopover onClose={() => {}} />)
    fireEvent.click(screen.getByLabelText('즐겨찾기만'))
    expect(useFileFiltersStore.getState().filters.starred).toBe(true)
  })

  it('초기화 → DEFAULT 로 reset', () => {
    useFileFiltersStore.setState({
      filters: { kinds: ['pdf'], modified: '7d', starred: true, shared: true },
    })
    render(<FilterPopover onClose={() => {}} />)
    fireEvent.click(screen.getByText('초기화'))
    expect(useFileFiltersStore.getState().filters).toEqual({
      kinds: [], modified: 'any', starred: false, shared: false,
    })
  })

  it('적용 버튼 클릭 → onClose 호출', () => {
    const onClose = vi.fn()
    render(<FilterPopover onClose={onClose} />)
    fireEvent.click(screen.getByText('적용'))
    expect(onClose).toHaveBeenCalled()
  })
})
