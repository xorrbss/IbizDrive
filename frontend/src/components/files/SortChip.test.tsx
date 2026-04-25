import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SortChip } from './SortChip'

const replaceMock = vi.fn()
let mockQuery = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

describe('SortChip', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mockQuery = ''
  })

  it('URL ?sort=updatedAt&dir=desc 반영하여 select/방향 표시', () => {
    mockQuery = 'sort=updatedAt&dir=desc'
    render(<SortChip />)
    const select = screen.getByLabelText('정렬 기준') as HTMLSelectElement
    expect(select.value).toBe('updatedAt')
    expect(screen.getByLabelText('오름차순으로 변경')).toBeTruthy()
  })

  it('select 변경 → router.replace에 ?sort=size 포함', () => {
    render(<SortChip />)
    const select = screen.getByLabelText('정렬 기준') as HTMLSelectElement
    fireEvent.change(select, { target: { value: 'size' } })
    expect(replaceMock).toHaveBeenCalled()
    const url = replaceMock.mock.calls[0][0] as string
    expect(url).toContain('sort=size')
    expect(url).toContain('dir=asc')
  })

  it('방향 토글 → asc ↔ desc', () => {
    mockQuery = 'sort=name&dir=asc'
    render(<SortChip />)
    fireEvent.click(screen.getByLabelText('내림차순으로 변경'))
    expect(replaceMock).toHaveBeenCalled()
    const url = replaceMock.mock.calls[0][0] as string
    expect(url).toContain('dir=desc')
  })
})
