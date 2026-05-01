import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SharesLink } from './SharesLink'

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(() => '/files/root'),
}))

describe('SharesLink', () => {
  it('href="/shares" 링크 렌더', () => {
    render(<SharesLink />)
    const link = screen.getByRole('link', { name: /받은 공유/ })
    expect(link.getAttribute('href')).toBe('/shares')
  })

  it('현재 라우트가 /shares가 아니면 aria-current 미지정', () => {
    render(<SharesLink />)
    const link = screen.getByRole('link', { name: /받은 공유/ })
    expect(link.getAttribute('aria-current')).toBeNull()
  })
})
