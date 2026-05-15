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

  // 회귀 가드 — invalid Tailwind token (`bg-bg-1`) 재발 방지.
  // globals.css `@theme inline` 은 `--color-surface-1` 만 노출하고 `--color-bg-1` 은 미정의.
  // `bg-bg-1` 사용 시 popover 컨테이너 배경이 transparent 로 렌더됨.
  it('popover 컨테이너 배경 토큰 — bg-surface-1 사용 (bg-bg-1 미사용)', () => {
    render(<FilterPopover onClose={() => {}} />)
    const dialog = screen.getByRole('dialog', { name: '파일 필터' })
    expect(dialog.className).toContain('bg-surface-1')
    expect(dialog.className).not.toMatch(/\bbg-bg-\d/)
  })
})
