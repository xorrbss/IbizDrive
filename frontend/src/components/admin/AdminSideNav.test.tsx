import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AdminSideNav } from './AdminSideNav'

const mockPathname = vi.fn()
vi.mock('next/navigation', () => ({
  usePathname: () => mockPathname(),
}))

describe('AdminSideNav (admin-dashboard 트랙 갱신)', () => {
  it('대시보드는 활성 링크 — /admin 정확 일치 시 aria-current="page"', () => {
    mockPathname.mockReturnValue('/admin')
    render(<AdminSideNav />)
    const link = screen.getByRole('link', { name: '대시보드' })
    expect(link.getAttribute('href')).toBe('/admin')
    expect(link.getAttribute('aria-current')).toBe('page')
  })

  it('대시보드는 /admin/users에서는 활성 아님 (exact 매칭 — prefix 오작동 가드)', () => {
    mockPathname.mockReturnValue('/admin/users')
    render(<AdminSideNav />)
    const link = screen.getByRole('link', { name: '대시보드' })
    expect(link.getAttribute('aria-current')).toBe(null)
  })

  it('"v1.x 예정" 영역에 대시보드가 더 이상 없다 (회귀 가드)', () => {
    mockPathname.mockReturnValue('/admin')
    render(<AdminSideNav />)
    // 활성 링크는 통과하지만 disabled span으로는 노출되면 안 됨 — 활성 링크는 1개만 존재해야 함.
    const allDashboard = screen.getAllByText('대시보드')
    expect(allDashboard).toHaveLength(1)
    const span = allDashboard[0].closest('[aria-disabled="true"]')
    expect(span).toBeNull()
  })
})
