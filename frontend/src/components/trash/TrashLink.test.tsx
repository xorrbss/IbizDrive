import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TrashLink } from './TrashLink'

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(() => '/files/root'),
}))

describe('TrashLink', () => {
  it('href="/trash" 링크 렌더', () => {
    render(<TrashLink />)
    const link = screen.getByRole('link', { name: /휴지통/ })
    expect(link.getAttribute('href')).toBe('/trash')
  })

  it('현재 라우트가 /trash가 아니면 aria-current 미지정', () => {
    render(<TrashLink />)
    const link = screen.getByRole('link', { name: /휴지통/ })
    expect(link.getAttribute('aria-current')).toBeNull()
  })
})
