import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ViewSwitch } from './ViewSwitch'

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

describe('ViewSwitch (M15.2)', () => {
  it('기본 — list active, grid 비활성', () => {
    render(<ViewSwitch />)
    expect(screen.getByRole('button', { name: '목록 뷰' }).getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByRole('button', { name: '그리드 뷰' }).getAttribute('aria-pressed')).toBe('false')
  })

  it('Grid 클릭 → ?view=grid 설정', () => {
    render(<ViewSwitch />)
    fireEvent.click(screen.getByRole('button', { name: '그리드 뷰' }))
    expect(replaceMock).toHaveBeenCalledTimes(1)
    expect(replaceMock.mock.calls[0][0]).toContain('view=grid')
  })

  it('Grid 활성 상태에서 List 클릭 → ?view 제거', () => {
    currentParams = new URLSearchParams('view=grid')
    render(<ViewSwitch />)
    expect(screen.getByRole('button', { name: '그리드 뷰' }).getAttribute('aria-pressed')).toBe('true')
    fireEvent.click(screen.getByRole('button', { name: '목록 뷰' }))
    const url = replaceMock.mock.calls[0][0] as string
    expect(url).not.toContain('view=')
  })
})
