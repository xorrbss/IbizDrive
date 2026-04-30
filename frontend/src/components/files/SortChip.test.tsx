import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SortChip } from './SortChip'

const replaceMock = vi.fn()
let currentParams = new URLSearchParams('')

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => currentParams,
}))

beforeEach(() => {
  replaceMock.mockClear()
  currentParams = new URLSearchParams('')
})

describe('SortChip (M15.1)', () => {
  it('기본 — 이름 / asc 표시 + aria-label', () => {
    render(<SortChip />)
    const btn = screen.getByRole('button', { name: /정렬: 이름 오름차순/ })
    expect(btn.textContent).toMatch(/이름/)
    expect(btn.textContent).toMatch(/↑/)
  })

  it('옵션 변경 → router.replace로 ?sort/?dir 갱신', () => {
    render(<SortChip />)
    fireEvent.click(screen.getByRole('button', { name: /정렬:/ }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: /수정일/ }))
    expect(replaceMock).toHaveBeenCalledTimes(1)
    const url = replaceMock.mock.calls[0][0] as string
    expect(url).toContain('sort=updatedAt')
    expect(url).toContain('dir=asc')
  })

  it('같은 key 재선택 → asc/desc 토글', () => {
    currentParams = new URLSearchParams('sort=name&dir=asc')
    render(<SortChip />)
    fireEvent.click(screen.getByRole('button', { name: /정렬:/ }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: /이름/ }))
    const url = replaceMock.mock.calls[0][0] as string
    expect(url).toContain('sort=name')
    expect(url).toContain('dir=desc')
  })
})
