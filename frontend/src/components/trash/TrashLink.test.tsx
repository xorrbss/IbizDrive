import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TrashLink } from './TrashLink'

const mockPathname = vi.fn<() => string>()

vi.mock('next/navigation', () => ({
  usePathname: () => mockPathname(),
}))

describe('TrashLink', () => {
  it('href는 /trash + 휴지통 라벨', () => {
    mockPathname.mockReturnValue('/files/root')
    render(<TrashLink />)
    const link = screen.getByRole('link', { name: /휴지통/ })
    expect(link.getAttribute('href')).toBe('/trash')
    expect(link.getAttribute('aria-current')).toBeNull()
  })

  it('/trash 경로일 때 aria-current="page"', () => {
    mockPathname.mockReturnValue('/trash')
    render(<TrashLink />)
    const link = screen.getByRole('link', { name: /휴지통/ })
    expect(link.getAttribute('aria-current')).toBe('page')
  })

  it('/trash 하위 경로(/trash/x)도 active', () => {
    mockPathname.mockReturnValue('/trash/x')
    render(<TrashLink />)
    const link = screen.getByRole('link', { name: /휴지통/ })
    expect(link.getAttribute('aria-current')).toBe('page')
  })
})
