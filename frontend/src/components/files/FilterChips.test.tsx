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

  // 회귀 가드 — invalid Tailwind token (`bg-bg-2`) 재발 방지.
  // globals.css `@theme inline` 은 `--color-surface-2` 만 노출하고 `--color-bg-2` 는 미정의.
  // `bg-bg-2` 사용 시 Tailwind 가 silent drop 하여 chip 배경이 transparent 로 렌더됨.
  it('chip 배경 토큰 — bg-surface-2 사용 (bg-bg-2 미사용)', () => {
    useFileFiltersStore.setState({
      filters: { ...DEFAULT_FILE_FILTERS, kinds: ['pdf'] },
    })
    render(<FilterChips />)
    const chip = screen.getByText('PDF').parentElement as HTMLElement
    expect(chip.className).toContain('bg-surface-2')
    expect(chip.className).not.toMatch(/\bbg-bg-\d/)
  })
})
