import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { FilterChips } from './FilterChips'
import { useFileFiltersStore } from '@/stores/fileFilters'
import { DEFAULT_FILE_FILTERS } from '@/types/fileFilters'

describe('FilterChips', () => {
  beforeEach(() => {
    useFileFiltersStore.getState().reset()
  })

  it('default — 미렌더 (DOM 차지 0)', () => {
    const { container } = render(<FilterChips />)
    expect(container.firstChild).toBeNull()
  })

  it('kinds + modified + starred + shared 모두 chip 표시', () => {
    useFileFiltersStore.setState({
      filters: { ...DEFAULT_FILE_FILTERS, kinds: ['pdf', 'image'], modified: '7d', starred: true, shared: true },
    })
    render(<FilterChips />)
    expect(screen.getByText('PDF')).toBeTruthy()
    expect(screen.getByText('이미지')).toBeTruthy()
    expect(screen.getByText('최근 7일')).toBeTruthy()
    expect(screen.getByText('즐겨찾기')).toBeTruthy()
    expect(screen.getByText('공유 항목')).toBeTruthy()
    expect(screen.getByText('전체 지우기')).toBeTruthy()
  })

  it('chip 제거 — 해당 필터만 unset', () => {
    useFileFiltersStore.setState({
      filters: { ...DEFAULT_FILE_FILTERS, kinds: ['pdf', 'image'], starred: true },
    })
    render(<FilterChips />)
    fireEvent.click(screen.getByLabelText('PDF 제거'))
    expect(useFileFiltersStore.getState().filters.kinds).toEqual(['image'])
    expect(useFileFiltersStore.getState().filters.starred).toBe(true)
  })

  it('전체 지우기 — 모든 필터 reset', () => {
    useFileFiltersStore.setState({
      filters: { ...DEFAULT_FILE_FILTERS, kinds: ['pdf'], modified: '7d', starred: true },
    })
    render(<FilterChips />)
    fireEvent.click(screen.getByText('전체 지우기'))
    expect(useFileFiltersStore.getState().filters).toEqual(DEFAULT_FILE_FILTERS)
  })
})
