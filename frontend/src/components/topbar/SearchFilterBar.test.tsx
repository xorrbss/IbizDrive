import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SearchFilterBar } from './SearchFilterBar'

/**
 * SearchFilterBar (ADR #52) — presentational 필터 바 단위 테스트.
 * 값/콜백만 검증 (필터→검색 배선은 SearchBar/e2e가 책임).
 */
describe('SearchFilterBar', () => {
  const base = {
    type: 'all' as const,
    onTypeChange: vi.fn(),
    myFilesOnly: false,
    onMyFilesOnlyChange: vi.fn(),
    myFilesDisabled: false,
  }

  it('종류 3개(전체/파일/폴더) + 현재 선택 aria-pressed', () => {
    render(<SearchFilterBar {...base} type="file" />)
    expect(screen.getByRole('button', { name: '전체' }).getAttribute('aria-pressed')).toBe('false')
    expect(screen.getByRole('button', { name: '파일' }).getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByRole('button', { name: '폴더' }).getAttribute('aria-pressed')).toBe('false')
  })

  it('종류 버튼 클릭 → onTypeChange(value)', () => {
    const onTypeChange = vi.fn()
    render(<SearchFilterBar {...base} onTypeChange={onTypeChange} />)
    fireEvent.click(screen.getByRole('button', { name: '폴더' }))
    expect(onTypeChange).toHaveBeenCalledWith('folder')
  })

  it('"내 파일만" 체크 → onMyFilesOnlyChange(true)', () => {
    const onMyFilesOnlyChange = vi.fn()
    render(<SearchFilterBar {...base} onMyFilesOnlyChange={onMyFilesOnlyChange} />)
    fireEvent.click(screen.getByRole('checkbox', { name: /내 파일만/ }))
    expect(onMyFilesOnlyChange).toHaveBeenCalledWith(true)
  })

  it('myFilesDisabled=true → 체크박스 disabled', () => {
    render(<SearchFilterBar {...base} myFilesDisabled />)
    expect((screen.getByRole('checkbox', { name: /내 파일만/ }) as HTMLInputElement).disabled).toBe(true)
  })
})
