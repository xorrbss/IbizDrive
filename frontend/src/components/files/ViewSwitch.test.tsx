import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ViewSwitch } from './ViewSwitch'

const replaceMock = vi.fn()
let mockQuery = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

describe('ViewSwitch', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mockQuery = ''
  })

  it('default → 목록 보기 aria-pressed=true', () => {
    render(<ViewSwitch />)
    expect(screen.getByLabelText('목록 보기').getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByLabelText('그리드 보기').getAttribute('aria-pressed')).toBe('false')
  })

  it('?view=grid → 그리드 보기 active', () => {
    mockQuery = 'view=grid'
    render(<ViewSwitch />)
    expect(screen.getByLabelText('그리드 보기').getAttribute('aria-pressed')).toBe('true')
  })

  it('그리드 클릭 → router.replace ?view=grid', () => {
    render(<ViewSwitch />)
    fireEvent.click(screen.getByLabelText('그리드 보기'))
    expect(replaceMock).toHaveBeenCalledWith('/files/root?view=grid', {
      scroll: false,
    })
  })
})
